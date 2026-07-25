package com.locationsetter.app.ui.setup

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.locationsetter.app.R
import com.locationsetter.app.databinding.FragmentDeviceSetupGuideBinding
import kotlinx.coroutines.launch

class DeviceSetupGuideFragment : Fragment() {

    private var _binding: FragmentDeviceSetupGuideBinding? = null
    private val binding get() = _binding!!

    private val viewModel: DeviceSetupGuideViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDeviceSetupGuideBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.openDeveloperOptionsButton.setOnClickListener {
            startActivity(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS))
        }
        binding.openAppSettingsButton.setOnClickListener {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = android.net.Uri.fromParts("package", requireContext().packageName, null)
            }
            startActivity(intent)
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect { state -> render(state) }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refresh()
    }

    private fun render(state: SetupState) {
        bindStep(
            statusIcon = binding.step1Status,
            statusText = binding.step1StatusText,
            passed = state.developerOptionsEnabled
        )
        bindStep(
            statusIcon = binding.step2Status,
            statusText = binding.step2StatusText,
            passed = state.mockLocationAppSelected
        )
        bindStep(
            statusIcon = binding.step3Status,
            statusText = binding.step3StatusText,
            passed = state.locationPermissionGranted
        )
    }

    private fun bindStep(
        statusIcon: android.widget.ImageView,
        statusText: android.widget.TextView,
        passed: Boolean
    ) {
        if (passed) {
            statusIcon.setImageResource(R.drawable.ic_check_circle)
            statusText.text = getString(R.string.setup_status_ok)
        } else {
            statusIcon.setImageResource(R.drawable.ic_error_circle)
            statusText.text = getString(R.string.setup_status_missing)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
