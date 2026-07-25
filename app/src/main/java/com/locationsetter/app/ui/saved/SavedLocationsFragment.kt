package com.locationsetter.app.ui.saved

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.locationsetter.app.LocationSetterApp
import com.locationsetter.app.R
import com.locationsetter.app.data.room.LocationEntity
import com.locationsetter.app.databinding.FragmentSavedLocationsBinding
import kotlinx.coroutines.launch

class SavedLocationsFragment : Fragment() {

    private var _binding: FragmentSavedLocationsBinding? = null
    private val binding get() = _binding!!

    private val viewModel: SavedLocationsViewModel by viewModels {
        SavedLocationsViewModelFactory((requireActivity().application as LocationSetterApp).container.locationRepository)
    }

    private lateinit var adapter: SavedLocationsAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSavedLocationsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = SavedLocationsAdapter(
            onUse = ::useLocation,
            onRename = ::showRenameDialog,
            onDelete = ::confirmDelete
        )
        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerView.adapter = adapter

        childFragmentManager.setFragmentResultListener(
            RenameLocationDialogFragment.REQUEST_KEY,
            viewLifecycleOwner
        ) { _, bundle ->
            val id = bundle.getLong(RenameLocationDialogFragment.RESULT_ID)
            val newName = bundle.getString(RenameLocationDialogFragment.RESULT_NAME).orEmpty()
            viewModel.rename(id, newName)
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.locations.collect { locations ->
                    adapter.submitList(locations)
                    binding.emptyStateText.visibility = if (locations.isEmpty()) View.VISIBLE else View.GONE
                }
            }
        }
    }

    private fun useLocation(location: LocationEntity) {
        val action = SavedLocationsFragmentDirections.actionSavedToMap(
            argLatitude = location.latitude.toString(),
            argLongitude = location.longitude.toString(),
            argLabel = location.name
        )
        findNavController().navigate(action)
    }

    private fun showRenameDialog(location: LocationEntity) {
        RenameLocationDialogFragment.newInstance(location.id, location.name)
            .show(childFragmentManager, "rename_location")
    }

    private fun confirmDelete(location: LocationEntity) {
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.action_delete)
            .setMessage(getString(R.string.confirm_delete_message, location.name))
            .setPositiveButton(R.string.action_delete) { _, _ -> viewModel.delete(location) }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
