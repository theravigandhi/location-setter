package com.locationsetter.app.ui.paywall

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.locationsetter.app.data.subscription.SubscriptionRepository

class PaywallViewModelFactory(private val repository: SubscriptionRepository) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(PaywallViewModel::class.java))
        return PaywallViewModel(repository) as T
    }
}
