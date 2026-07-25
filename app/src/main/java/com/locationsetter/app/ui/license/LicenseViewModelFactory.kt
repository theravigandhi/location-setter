package com.locationsetter.app.ui.license

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.locationsetter.app.data.license.LicenseRepository

class LicenseViewModelFactory(private val repository: LicenseRepository) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(LicenseViewModel::class.java))
        return LicenseViewModel(repository) as T
    }
}
