package com.locationsetter.app.ui.paywall

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
import com.locationsetter.app.databinding.FragmentPaywallBinding
import com.locationsetter.app.model.SubscriptionState
import kotlinx.coroutines.launch

class PaywallFragment : Fragment() {

    private var _binding: FragmentPaywallBinding? = null
    private val binding get() = _binding!!

    private val viewModel: PaywallViewModel by viewModels {
        PaywallViewModelFactory((requireActivity().application as LocationSetterApp).container.subscriptionRepository)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPaywallBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.toolbar.setNavigationOnClickListener { findNavController().navigateUp() }

        bindBenefits()

        binding.subscribeButton.setOnClickListener { openCheckout() }
        binding.activateButton.setOnClickListener {
            viewModel.activateLicense(binding.licenseInput.text?.toString().orEmpty())
        }
        binding.verifyButton.setOnClickListener { viewModel.refreshLicenseStatus() }

        observeViewModel()
    }

    private fun bindBenefits() {
        binding.benefit1.text.setText(R.string.paywall_benefit_unlimited)
        binding.benefit2.text.setText(R.string.paywall_benefit_unlimited_saves)
        binding.benefit3.text.setText(R.string.paywall_benefit_support)
        binding.benefit4.text.setText(R.string.paywall_benefit_updates)
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.subscriptionState.collect { state -> renderState(state) }
                }
                launch {
                    viewModel.isLoading.collect { loading ->
                        binding.loadingIndicator.visibility = if (loading) View.VISIBLE else View.GONE
                        binding.subscribeButton.isEnabled = !loading
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

    private fun renderState(state: SubscriptionState) {
        binding.verifyButton.visibility = if (state.licenseKey != null) View.VISIBLE else View.GONE
        binding.statusText.text = when {
            state.isSubscribed -> getString(R.string.paywall_subscribed_status)
            state.trialActivationsRemaining > 0 ->
                getString(R.string.paywall_trial_remaining_format, state.trialActivationsRemaining)
            else -> getString(R.string.paywall_trial_used_up)
        }
    }

    private fun openCheckout() {
        val url = BuildConfig.LEMONSQUEEZY_CHECKOUT_URL
        CustomTabsIntent.Builder().build().launchUrl(requireContext(), Uri.parse(url))
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
