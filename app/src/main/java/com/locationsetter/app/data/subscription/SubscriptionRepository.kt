package com.locationsetter.app.data.subscription

import android.content.Context
import androidx.core.content.edit
import com.locationsetter.app.model.SubscriptionState
import com.locationsetter.app.util.Constants
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Persists subscription/trial state locally via SharedPreferences. There is no backend, so this
 * is the app's own source of truth for entitlement, periodically re-synced against Lemon
 * Squeezy's license API (see [LemonSqueezyApi]) rather than trusted blindly forever.
 */
class SubscriptionRepository(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(Constants.SUBSCRIPTION_PREFS_NAME, Context.MODE_PRIVATE)

    private val _state = MutableStateFlow(readState())
    val state: StateFlow<SubscriptionState> = _state.asStateFlow()

    fun recordTrialActivation() {
        if (_state.value.isSubscribed) return
        val updated = _state.value.copy(trialActivationsUsed = _state.value.trialActivationsUsed + 1)
        persist(updated)
    }

    fun saveLicense(licenseKey: String, instanceId: String?, isActive: Boolean) {
        val updated = _state.value.copy(
            isSubscribed = isActive,
            licenseKey = licenseKey,
            instanceId = instanceId,
            lastVerifiedAtMillis = System.currentTimeMillis()
        )
        persist(updated)
    }

    fun markVerified(isActive: Boolean) {
        val updated = _state.value.copy(
            isSubscribed = isActive,
            lastVerifiedAtMillis = System.currentTimeMillis()
        )
        persist(updated)
    }

    fun clearLicense() {
        persist(SubscriptionState(trialActivationsUsed = _state.value.trialActivationsUsed))
    }

    private fun persist(newState: SubscriptionState) {
        prefs.edit {
            putBoolean(KEY_SUBSCRIBED, newState.isSubscribed)
            putString(KEY_LICENSE_KEY, newState.licenseKey)
            putString(KEY_INSTANCE_ID, newState.instanceId)
            putInt(KEY_TRIAL_USED, newState.trialActivationsUsed)
            putLong(KEY_LAST_VERIFIED, newState.lastVerifiedAtMillis)
        }
        _state.value = newState
    }

    private fun readState(): SubscriptionState = SubscriptionState(
        isSubscribed = prefs.getBoolean(KEY_SUBSCRIBED, false),
        licenseKey = prefs.getString(KEY_LICENSE_KEY, null),
        instanceId = prefs.getString(KEY_INSTANCE_ID, null),
        trialActivationsUsed = prefs.getInt(KEY_TRIAL_USED, 0),
        lastVerifiedAtMillis = prefs.getLong(KEY_LAST_VERIFIED, 0L)
    )

    private companion object {
        const val KEY_SUBSCRIBED = "is_subscribed"
        const val KEY_LICENSE_KEY = "license_key"
        const val KEY_INSTANCE_ID = "instance_id"
        const val KEY_TRIAL_USED = "trial_activations_used"
        const val KEY_LAST_VERIFIED = "last_verified_at"
    }
}
