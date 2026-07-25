package com.locationsetter.app.ui.license

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.locationsetter.app.data.license.LicenseRepository
import com.locationsetter.app.model.LicenseState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class LicenseViewModel(private val repository: LicenseRepository) : ViewModel() {

    val licenseState: StateFlow<LicenseState> = repository.state

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    fun redeemCode(code: String, successMessage: String, notFoundMessage: String) {
        if (code.isBlank()) {
            _message.value = notFoundMessage
            return
        }
        viewModelScope.launch {
            _isLoading.value = true
            val result = repository.redeemCode(code)
            _isLoading.value = false
            _message.value = if (result.isSuccess) {
                successMessage
            } else {
                result.exceptionOrNull()?.message ?: notFoundMessage
            }
        }
    }

    fun refreshStatus(failureMessage: String) {
        viewModelScope.launch {
            _isLoading.value = true
            val ok = repository.refreshStatus()
            _isLoading.value = false
            if (!ok) _message.value = failureMessage
        }
    }

    fun consumeMessage() {
        _message.value = null
    }
}
