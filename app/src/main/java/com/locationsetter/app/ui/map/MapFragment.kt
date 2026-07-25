package com.locationsetter.app.ui.map

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Intent
import android.location.Geocoder
import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.widget.Autocomplete
import com.google.android.libraries.places.widget.model.AutocompleteActivityMode
import com.locationsetter.app.LocationSetterApp
import com.locationsetter.app.R
import com.locationsetter.app.databinding.FragmentMapBinding
import com.locationsetter.app.service.MockLocationService
import com.locationsetter.app.util.Constants
import com.locationsetter.app.util.DeveloperOptionsChecker
import com.locationsetter.app.util.MockLocationChecker
import com.locationsetter.app.util.PermissionUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Locale

class MapFragment : Fragment(), OnMapReadyCallback {

    private var _binding: FragmentMapBinding? = null
    private val binding get() = _binding!!

    private val navArgs: MapFragmentArgs by navArgs()

    private val viewModel: MapViewModel by viewModels {
        val container = (requireActivity().application as LocationSetterApp).container
        MapViewModelFactory(container.locationRepository, container.subscriptionRepository)
    }

    private var googleMap: GoogleMap? = null
    private var currentMarker: com.google.android.gms.maps.model.Marker? = null
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var statusPulseAnimator: ObjectAnimator? = null
    private var geocodeJob: Job? = null
    private var suppressCameraRecenter = false

    private val autocompleteLauncher =
        registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()) { result ->
            val data = result.data
            if (result.resultCode != android.app.Activity.RESULT_OK || data == null) {
                // User cancelled the search, or it errored out — nothing to do, and importantly
                // nothing safe to parse from `data` in either case.
                return@registerForActivityResult
            }
            try {
                val place = Autocomplete.getPlaceFromIntent(data)
                val latLng = place.latLng ?: return@registerForActivityResult
                onLocationChosen(latLng.latitude, latLng.longitude, place.name)
            } catch (e: Exception) {
                Toast.makeText(requireContext(), R.string.search_failed, Toast.LENGTH_SHORT).show()
            }
        }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMapBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        if (!Places.isInitialized()) {
            Places.initialize(requireContext().applicationContext, com.locationsetter.app.BuildConfig.MAPS_API_KEY)
        }
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireContext())

        val mapFragment = childFragmentManager.findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)

        navArgs.argLatitude?.toDoubleOrNull()?.let { lat ->
            val lng = navArgs.argLongitude?.toDoubleOrNull()
            if (lng != null) {
                viewModel.selectLocation(lat, lng, navArgs.argLabel)
            }
        }

        binding.searchBar.setOnClickListener { launchAutocomplete() }

        binding.saveButton.setOnClickListener { showSaveDialog() }

        binding.setMockLocationButton.setOnClickListener { onMockButtonClicked() }

        binding.setupBannerRow.setOnClickListener {
            findNavController().navigate(R.id.action_map_to_deviceSetupGuide)
        }

        observeViewModel()
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.selectedLocation.collect { selected ->
                        renderSelectedLocation(selected)
                    }
                }
                launch {
                    viewModel.mockStatus.collect { status ->
                        renderMockStatus(status)
                    }
                }
                launch {
                    viewModel.subscriptionState.collect { subscription ->
                        if (subscription.isSubscribed) {
                            binding.trialInfoText.visibility = View.GONE
                        } else {
                            binding.trialInfoText.visibility = View.VISIBLE
                            binding.trialInfoText.text = if (subscription.trialActivationsRemaining > 0) {
                                getString(R.string.paywall_trial_remaining_format, subscription.trialActivationsRemaining)
                            } else {
                                getString(R.string.paywall_trial_used_up)
                            }
                        }
                    }
                }
            }
        }
    }

    private fun renderSelectedLocation(selected: SelectedLocation?) {
        if (selected == null) {
            binding.selectedCoordinatesText.text = getString(R.string.no_location_selected)
            binding.selectedAddressText.visibility = View.GONE
            currentMarker?.remove()
            currentMarker = null
            geocodeJob?.cancel()
            return
        }

        binding.selectedCoordinatesText.text = getString(
            R.string.coordinates_format,
            selected.latitude,
            selected.longitude
        )
        if (selected.address != null) {
            binding.selectedAddressText.text = selected.address
            binding.selectedAddressText.visibility = View.VISIBLE
        } else {
            binding.selectedAddressText.visibility = View.GONE
            resolveAddressForSelection(selected)
        }

        val latLng = LatLng(selected.latitude, selected.longitude)
        val markerTitle = selected.label ?: selected.address ?: getString(R.string.selected_location)
        val marker = currentMarker
        if (marker != null) {
            // Update the existing marker in place rather than remove+recreate, so a marker being
            // actively dragged doesn't flicker when this re-renders in response to its own drag.
            marker.position = latLng
            marker.title = markerTitle
        } else {
            currentMarker = googleMap?.addMarker(
                MarkerOptions().position(latLng).title(markerTitle).draggable(true)
            )
        }

        if (suppressCameraRecenter) {
            suppressCameraRecenter = false
        } else {
            googleMap?.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, 16f))
        }
    }

    private fun resolveAddressForSelection(selected: SelectedLocation) {
        geocodeJob?.cancel()
        geocodeJob = viewLifecycleOwner.lifecycleScope.launch {
            val address = withContext(Dispatchers.IO) {
                try {
                    if (!Geocoder.isPresent()) return@withContext null
                    @Suppress("DEPRECATION")
                    val results = Geocoder(requireContext(), Locale.getDefault())
                        .getFromLocation(selected.latitude, selected.longitude, 1)
                    results?.firstOrNull()?.getAddressLine(0)
                } catch (e: Exception) {
                    // Geocoding is a best-effort convenience (network/service can fail or be
                    // unavailable on some devices) — coordinates are already shown regardless.
                    null
                }
            }
            if (address != null) {
                viewModel.updateAddressForCurrentSelection(selected.latitude, selected.longitude, address)
            }
        }
    }

    private fun renderMockStatus(status: com.locationsetter.app.model.MockLocationStatus) {
        if (status.isRunning) {
            binding.statusChip.text = getString(R.string.status_running)
            binding.statusChip.setBackgroundResource(R.drawable.bg_status_chip_running)
            binding.statusChip.setTextColor(ContextCompat.getColor(requireContext(), R.color.status_running_fg))
            binding.setMockLocationButton.text = getString(R.string.action_stop_mock_location)
            val time = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(status.lastUpdateMillis)
            binding.lastUpdateText.text = getString(R.string.last_update_format, time)
            binding.lastUpdateText.visibility = View.VISIBLE
            startStatusPulse()
        } else {
            binding.statusChip.text = getString(R.string.status_stopped)
            binding.statusChip.setBackgroundResource(R.drawable.bg_status_chip_stopped)
            binding.statusChip.setTextColor(ContextCompat.getColor(requireContext(), R.color.status_stopped_fg))
            binding.setMockLocationButton.text = getString(R.string.action_set_mock_location)
            binding.lastUpdateText.visibility = View.GONE
            stopStatusPulse()
        }
    }

    private fun startStatusPulse() {
        if (statusPulseAnimator?.isRunning == true) return
        statusPulseAnimator = ObjectAnimator.ofFloat(binding.statusChip, View.ALPHA, 1f, 0.55f, 1f).apply {
            duration = 1400
            repeatCount = ValueAnimator.INFINITE
            start()
        }
    }

    private fun stopStatusPulse() {
        statusPulseAnimator?.cancel()
        statusPulseAnimator = null
        binding.statusChip.alpha = 1f
    }

    private fun launchAutocomplete() {
        val fields = listOf(Place.Field.ID, Place.Field.NAME, Place.Field.LAT_LNG)
        val intent = Autocomplete.IntentBuilder(AutocompleteActivityMode.OVERLAY, fields)
            .build(requireContext())
        autocompleteLauncher.launch(intent)
    }

    private fun onLocationChosen(latitude: Double, longitude: Double, label: String?) {
        viewModel.selectLocation(latitude, longitude, label)
    }

    private fun showSaveDialog() {
        if (viewModel.selectedLocation.value == null) {
            Toast.makeText(requireContext(), R.string.no_location_selected, Toast.LENGTH_SHORT).show()
            return
        }
        val input = EditText(requireContext()).apply {
            inputType = InputType.TYPE_CLASS_TEXT
            hint = getString(R.string.location_name_hint)
        }
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.action_save_location)
            .setView(input)
            .setPositiveButton(R.string.action_save) { _, _ ->
                val name = input.text.toString().trim().ifBlank { getString(R.string.default_location_name) }
                viewModel.saveSelectedLocation(name)
                Toast.makeText(requireContext(), R.string.location_saved, Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }

    private fun onMockButtonClicked() {
        val running = viewModel.mockStatus.value.isRunning
        if (running) {
            stopMocking()
            return
        }
        val selected = viewModel.selectedLocation.value
        if (selected == null) {
            Toast.makeText(requireContext(), R.string.select_location_first, Toast.LENGTH_SHORT).show()
            return
        }
        if (!viewModel.subscriptionState.value.canStartMocking) {
            findNavController().navigate(R.id.action_map_to_paywall)
            return
        }
        viewLifecycleOwner.lifecycleScope.launch {
            val configured = withContext(Dispatchers.IO) {
                DeveloperOptionsChecker.isDeveloperOptionsEnabled(requireContext()) &&
                    MockLocationChecker.isSelectedAsMockLocationApp(requireContext())
            }
            if (!configured) {
                binding.setupBanner.visibility = View.VISIBLE
                Toast.makeText(requireContext(), R.string.setup_banner_text, Toast.LENGTH_LONG).show()
                return@launch
            }
            binding.setupBanner.visibility = View.GONE
            viewModel.recordTrialActivation()
            startMocking(selected)
        }
    }

    private fun startMocking(selected: SelectedLocation) {
        val intent = Intent(requireContext(), MockLocationService::class.java).apply {
            action = Constants.ACTION_START_MOCKING
            putExtra(Constants.EXTRA_LATITUDE, selected.latitude)
            putExtra(Constants.EXTRA_LONGITUDE, selected.longitude)
            putExtra(Constants.EXTRA_LABEL, selected.label)
        }
        ContextCompat.startForegroundService(requireContext(), intent)
    }

    private fun stopMocking() {
        val intent = Intent(requireContext(), MockLocationService::class.java).apply {
            action = Constants.ACTION_STOP_MOCKING
        }
        ContextCompat.startForegroundService(requireContext(), intent)
    }

    override fun onMapReady(map: GoogleMap) {
        googleMap = map
        if (PermissionUtils.hasFineLocationPermission(requireContext())) {
            try {
                map.isMyLocationEnabled = true
            } catch (_: SecurityException) {
                // Permission revoked between check and call; ignore, UI already reflects unmocked state.
            }
            moveToCurrentLocation()
        }
        map.setOnMapLongClickListener { latLng ->
            viewModel.selectLocation(latLng.latitude, latLng.longitude, null)
        }
        map.setOnMarkerDragListener(object : GoogleMap.OnMarkerDragListener {
            override fun onMarkerDragStart(marker: com.google.android.gms.maps.model.Marker) = Unit
            override fun onMarkerDrag(marker: com.google.android.gms.maps.model.Marker) = Unit
            override fun onMarkerDragEnd(marker: com.google.android.gms.maps.model.Marker) {
                suppressCameraRecenter = true
                viewModel.selectLocation(marker.position.latitude, marker.position.longitude, null)
            }
        })
        viewModel.selectedLocation.value?.let { renderSelectedLocation(it) }
    }

    private fun moveToCurrentLocation() {
        if (!PermissionUtils.hasFineLocationPermission(requireContext())) return
        try {
            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                if (location != null && viewModel.selectedLocation.value == null) {
                    googleMap?.animateCamera(
                        CameraUpdateFactory.newLatLngZoom(
                            LatLng(location.latitude, location.longitude),
                            14f
                        )
                    )
                }
            }
        } catch (_: SecurityException) {
            // No-op: location permission was revoked.
        }
    }

    override fun onDestroyView() {
        statusPulseAnimator?.cancel()
        statusPulseAnimator = null
        super.onDestroyView()
        _binding = null
    }
}
