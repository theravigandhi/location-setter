package com.locationsetter.app.ui.paywall

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.locationsetter.app.data.subscription.LemonSqueezyApi
import com.locationsetter.app.data.subscription.SubscriptionRepository
import com.locationsetter.app.model.SubscriptionState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

class PaywallViewModel(private val repository: SubscriptionRepository) : ViewModel() {

    val subscriptionState: StateFlow<SubscriptionState> = repository.state

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    fun activateLicense(licenseKey: String) {
        val trimmed = licenseKey.trim()
        if (trimmed.isEmpty()) {
            _message.value = "Enter a license key first"
            return
        }
        viewModelScope.launch {
            _isLoading.value = true
            val instanceName = "location-setter-${UUID.randomUUID()}"
            val result = LemonSqueezyApi.activate(trimmed, instanceName)
            _isLoading.value = false
            if (result.valid) {
                repository.saveLicense(trimmed, result.instanceId, isActive = true)
                _message.value = "Subscription activated — thank you!"
            } else {
                _message.value = result.errorMessage ?: "That license key isn't valid"
            }
        }
    }

    fun refreshLicenseStatus() {
        val current = subscriptionState.value
        val key = current.licenseKey ?: return
        viewModelScope.launch {
            _isLoading.value = true
            val result = LemonSqueezyApi.validate(key, current.instanceId)
            _isLoading.value = false
            repository.markVerified(result.valid)
            _message.value = if (result.valid) {
                "Subscription is active"
            } else {
                result.errorMessage ?: "Subscription is no longer active"
            }
        }
    }

    fun consumeMessage() {
        _message.value = null
    }
}
