package com.locationsetter.app.data.license

import android.content.Context
import androidx.core.content.edit
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.locationsetter.app.model.LicenseState
import com.locationsetter.app.util.Constants
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await

/**
 * Firestore-backed license/session tracking. There is no in-app payment flow — payment is
 * collected manually (UPI/QR) outside the app, and the business issues a code via the admin
 * tool after confirming payment. This repository is the app's read-through cache; the Firestore
 * document (see admin-tool/ and firestore.rules) is the actual source of truth, since a purely
 * local counter could be reset by clearing app data.
 */
class LicenseRepository(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(Constants.LICENSE_PREFS_NAME, Context.MODE_PRIVATE)
    private val firestore by lazy { FirebaseFirestore.getInstance() }
    private val auth by lazy { FirebaseAuth.getInstance() }

    private val _state = MutableStateFlow(readState())
    val state: StateFlow<LicenseState> = _state.asStateFlow()

    /** Local-only free trial for brand-new installs before any code is redeemed. */
    fun recordTrialActivation() {
        if (_state.value.hasRedeemedCode) return
        persist(_state.value.copy(trialActivationsUsed = _state.value.trialActivationsUsed + 1))
    }

    suspend fun redeemCode(code: String): Result<Unit> {
        val trimmed = code.trim()
        if (trimmed.isEmpty()) return Result.failure(IllegalArgumentException("Enter a code"))
        return try {
            ensureAuthenticated()
            val doc = firestore.collection(Constants.LICENSES_COLLECTION).document(trimmed).get().await()
            if (!doc.exists()) {
                Result.failure(NoSuchElementException("That code wasn't found"))
            } else {
                applyDocSnapshot(trimmed, doc)
                Result.success(Unit)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Re-syncs the redeemed license's status (used by the license screen's refresh action). */
    suspend fun refreshStatus(): Boolean {
        val code = _state.value.redeemedCode ?: return false
        return try {
            ensureAuthenticated()
            val doc = firestore.collection(Constants.LICENSES_COLLECTION).document(code).get().await()
            if (doc.exists()) {
                applyDocSnapshot(code, doc)
                true
            } else {
                false
            }
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Atomically consumes one session against the redeemed license. The eligibility check
     * (active, not expired, quota remaining) happens inside the Firestore transaction itself —
     * not just in local state — so it can't be bypassed by a stale local cache.
     */
    suspend fun consumeSession(): Boolean {
        val code = _state.value.redeemedCode ?: return false
        return try {
            ensureAuthenticated()
            val docRef = firestore.collection(Constants.LICENSES_COLLECTION).document(code)
            val updatedUsed = firestore.runTransaction { transaction ->
                val snapshot = transaction.get(docRef)
                val allotted = (snapshot.getLong("sessionsAllotted") ?: 0L).toInt()
                val used = (snapshot.getLong("sessionsUsed") ?: 0L).toInt()
                val active = snapshot.getBoolean("active") ?: false
                val periodEndMillis = snapshot.getTimestamp("periodEnd")?.toDate()?.time ?: 0L
                if (!active || used >= allotted || System.currentTimeMillis() > periodEndMillis) {
                    throw IllegalStateException("License is inactive, expired, or out of sessions")
                }
                val newUsed = used + 1
                transaction.update(docRef, "sessionsUsed", newUsed.toLong())
                newUsed
            }.await()
            persist(_state.value.copy(sessionsUsed = updatedUsed, lastSyncedAtMillis = System.currentTimeMillis()))
            true
        } catch (e: Exception) {
            false
        }
    }

    fun clearLicense() {
        persist(LicenseState(trialActivationsUsed = _state.value.trialActivationsUsed))
    }

    private suspend fun ensureAuthenticated() {
        if (auth.currentUser != null) return
        auth.signInAnonymously().await()
    }

    private fun applyDocSnapshot(code: String, doc: DocumentSnapshot) {
        persist(
            LicenseState(
                trialActivationsUsed = _state.value.trialActivationsUsed,
                redeemedCode = code,
                sessionsAllotted = (doc.getLong("sessionsAllotted") ?: 0L).toInt(),
                sessionsUsed = (doc.getLong("sessionsUsed") ?: 0L).toInt(),
                periodEndMillis = doc.getTimestamp("periodEnd")?.toDate()?.time ?: 0L,
                active = doc.getBoolean("active") ?: false,
                lastSyncedAtMillis = System.currentTimeMillis()
            )
        )
    }

    private fun persist(newState: LicenseState) {
        prefs.edit {
            putInt(KEY_TRIAL_USED, newState.trialActivationsUsed)
            putString(KEY_CODE, newState.redeemedCode)
            putInt(KEY_ALLOTTED, newState.sessionsAllotted)
            putInt(KEY_USED, newState.sessionsUsed)
            putLong(KEY_PERIOD_END, newState.periodEndMillis)
            putBoolean(KEY_ACTIVE, newState.active)
            putLong(KEY_LAST_SYNC, newState.lastSyncedAtMillis)
        }
        _state.value = newState
    }

    private fun readState(): LicenseState = LicenseState(
        trialActivationsUsed = prefs.getInt(KEY_TRIAL_USED, 0),
        redeemedCode = prefs.getString(KEY_CODE, null),
        sessionsAllotted = prefs.getInt(KEY_ALLOTTED, 0),
        sessionsUsed = prefs.getInt(KEY_USED, 0),
        periodEndMillis = prefs.getLong(KEY_PERIOD_END, 0L),
        active = prefs.getBoolean(KEY_ACTIVE, false),
        lastSyncedAtMillis = prefs.getLong(KEY_LAST_SYNC, 0L)
    )

    private companion object {
        const val KEY_TRIAL_USED = "trial_activations_used"
        const val KEY_CODE = "redeemed_code"
        const val KEY_ALLOTTED = "sessions_allotted"
        const val KEY_USED = "sessions_used"
        const val KEY_PERIOD_END = "period_end_millis"
        const val KEY_ACTIVE = "active"
        const val KEY_LAST_SYNC = "last_synced_at_millis"
    }
}
