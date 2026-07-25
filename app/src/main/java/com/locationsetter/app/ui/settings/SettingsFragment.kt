package com.locationsetter.app.ui.settings

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.locationsetter.app.BuildConfig
import com.locationsetter.app.LocationSetterApp
import com.locationsetter.app.R
import com.locationsetter.app.databinding.FragmentSettingsBinding
import com.locationsetter.app.model.LicenseState
import kotlinx.coroutines.launch

class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.versionText.text = getString(R.string.app_version_format, BuildConfig.VERSION_NAME)

        binding.deviceSetupGuideRow.setOnClickListener {
            findNavController().navigate(R.id.action_settings_to_deviceSetupGuide)
        }

        binding.subscriptionStatusRow.setOnClickListener {
            findNavController().navigate(R.id.action_settings_to_license)
        }

        binding.openDeveloperOptionsRow.setOnClickListener {
            startActivity(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS))
        }

        binding.openAppSettingsRow.setOnClickListener {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = android.net.Uri.fromParts("package", requireContext().packageName, null)
            }
            startActivity(intent)
        }

        observeLicenseState()
    }

    private fun observeLicenseState() {
        val licenseRepository =
            (requireActivity().application as LocationSetterApp).container.licenseRepository
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                licenseRepository.state.collect { state -> renderLicenseStatus(state) }
            }
        }
    }

    private fun renderLicenseStatus(state: LicenseState) {
        binding.subscriptionStatusText.text = when {
            state.hasRedeemedCode && state.canStartMocking ->
                getString(R.string.sessions_remaining_format, state.sessionsRemaining)
            state.hasRedeemedCode ->
                getString(R.string.license_expired_or_exhausted)
            state.trialActivationsRemaining > 0 ->
                getString(R.string.trial_remaining_format, state.trialActivationsRemaining)
            else ->
                getString(R.string.trial_used_up)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
