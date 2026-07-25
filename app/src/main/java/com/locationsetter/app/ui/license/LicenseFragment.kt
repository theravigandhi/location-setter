package com.locationsetter.app.ui.license

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.browser.customtabs.CustomTabsIntent
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.locationsetter.app.BuildConfig
import com.locationsetter.app.LocationSetterApp
import com.locationsetter.app.R
import com.locationsetter.app.databinding.FragmentLicenseBinding
import com.locationsetter.app.model.LicenseState
import com.locationsetter.app.util.Constants
import kotlinx.coroutines.launch

class LicenseFragment : Fragment() {

    private var _binding: FragmentLicenseBinding? = null
    private val binding get() = _binding!!

    private val viewModel: LicenseViewModel by viewModels {
        LicenseViewModelFactory((requireActivity().application as LocationSetterApp).container.licenseRepository)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLicenseBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.toolbar.setNavigationOnClickListener { findNavController().navigateUp() }

        bindBenefits()

        binding.payUpiButton.setOnClickListener { payViaUpi() }
        binding.contactButton.setOnClickListener { openContactLink() }
        binding.activateButton.setOnClickListener {
            viewModel.redeemCode(
                binding.licenseInput.text?.toString().orEmpty(),
                getString(R.string.license_redeemed_success),
                getString(R.string.license_redeem_hint)
            )
        }
        binding.verifyButton.setOnClickListener {
            viewModel.refreshStatus(getString(R.string.session_consume_failed))
        }

        observeViewModel()
    }

    private fun bindBenefits() {
        binding.benefit1.text.setText(R.string.license_benefit_sessions)
        binding.benefit2.text.setText(R.string.license_benefit_saves)
        binding.benefit3.text.setText(R.string.license_benefit_support)
        binding.benefit4.text.setText(R.string.license_benefit_updates)
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.licenseState.collect { state -> renderState(state) }
                }
                launch {
                    viewModel.isLoading.collect { loading ->
                        binding.loadingIndicator.visibility = if (loading) View.VISIBLE else View.GONE
                        binding.payUpiButton.isEnabled = !loading
                        binding.activateButton.isEnabled = !loading
                        binding.verifyButton.isEnabled = !loading
                    }
                }
                launch {
                    viewModel.message.collect { message ->
                        if (message != null) {
                            Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show()
                            viewModel.consumeMessage()
                        }
                    }
                }
            }
        }
    }

    private fun renderState(state: LicenseState) {
        binding.verifyButton.visibility = if (state.hasRedeemedCode) View.VISIBLE else View.GONE
        binding.statusText.text = when {
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

    private fun payViaUpi() {
        val uri = Uri.Builder()
            .scheme("upi")
            .authority("pay")
            .appendQueryParameter("pa", BuildConfig.UPI_ID)
            .appendQueryParameter("pn", BuildConfig.UPI_PAYEE_NAME)
            .appendQueryParameter("am", Constants.UPI_PAYMENT_AMOUNT.toString())
            .appendQueryParameter("cu", "INR")
            .appendQueryParameter("tn", "Location Setter Pro")
            .build()
        try {
            startActivity(Intent(Intent.ACTION_VIEW, uri))
        } catch (e: Exception) {
            Toast.makeText(requireContext(), R.string.license_no_upi_app, Toast.LENGTH_LONG).show()
        }
    }

    private fun openContactLink() {
        val uri = Uri.parse(BuildConfig.LICENSE_CONTACT_URL)
        try {
            CustomTabsIntent.Builder().build().launchUrl(requireContext(), uri)
        } catch (e: Exception) {
            try {
                startActivity(Intent(Intent.ACTION_VIEW, uri))
            } catch (e2: Exception) {
                Toast.makeText(requireContext(), R.string.no_browser_available, Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
