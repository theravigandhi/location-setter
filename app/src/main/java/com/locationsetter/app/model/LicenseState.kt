package com.locationsetter.app.model

import com.locationsetter.app.util.Constants

data class LicenseState(
    val trialActivationsUsed: Int = 0,
    val redeemedCode: String? = null,
    val sessionsAllotted: Int = 0,
    val sessionsUsed: Int = 0,
    val periodEndMillis: Long = 0L,
    val active: Boolean = false,
    val lastSyncedAtMillis: Long = 0L
) {
    val hasRedeemedCode: Boolean
        get() = redeemedCode != null

    val trialActivationsRemaining: Int
        get() = (Constants.FREE_TRIAL_ACTIVATIONS - trialActivationsUsed).coerceAtLeast(0)

    val sessionsRemaining: Int
        get() = (sessionsAllotted - sessionsUsed).coerceAtLeast(0)

    val isExpired: Boolean
        get() = hasRedeemedCode && System.currentTimeMillis() > periodEndMillis

    /** Whether the user is currently allowed to start a new mock-location session. */
    val canStartMocking: Boolean
        get() = if (hasRedeemedCode) {
            active && !isExpired && sessionsRemaining > 0
        } else {
            trialActivationsUsed < Constants.FREE_TRIAL_ACTIVATIONS
        }
}
