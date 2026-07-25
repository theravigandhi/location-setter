package com.locationsetter.app.model

import com.locationsetter.app.util.Constants

data class SubscriptionState(
    val isSubscribed: Boolean = false,
    val licenseKey: String? = null,
    val instanceId: String? = null,
    val trialActivationsUsed: Int = 0,
    val lastVerifiedAtMillis: Long = 0L
) {
    val trialActivationsRemaining: Int
        get() = (Constants.FREE_TRIAL_ACTIVATIONS - trialActivationsUsed).coerceAtLeast(0)

    val canStartMocking: Boolean
        get() = isSubscribed || trialActivationsUsed < Constants.FREE_TRIAL_ACTIVATIONS
}
