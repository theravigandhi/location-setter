package com.locationsetter.app.ui.saved

import android.graphics.Canvas
import android.graphics.Paint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
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
        setupSwipeToDelete()

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
                    binding.emptyStateGroup.visibility = if (locations.isEmpty()) View.VISIBLE else View.GONE
                }
            }
        }
    }

    private fun setupSwipeToDelete() {
        val deleteIcon = ContextCompat.getDrawable(requireContext(), R.drawable.ic_delete)?.mutate()?.apply {
            setTint(ContextCompat.getColor(requireContext(), R.color.md_on_error_container))
        }
        val backgroundPaint = Paint().apply {
            color = ContextCompat.getColor(requireContext(), R.color.md_error_container)
            isAntiAlias = true
        }
        val cornerRadius = resources.displayMetrics.density * 16f

        val callback = object : ItemTouchHelper.SimpleCallback(
            0,
            ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT
        ) {
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ) = false

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val position = viewHolder.bindingAdapterPosition
                if (position == RecyclerView.NO_POSITION) return
                val location = adapter.currentList.getOrNull(position) ?: return
                // Snap the row back visually now; it's only actually removed from the list (and
                // from this UI) once the user confirms the delete dialog.
                adapter.notifyItemChanged(position)
                confirmDelete(location)
            }

            override fun onChildDraw(
                c: Canvas,
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                dX: Float,
                dY: Float,
                actionState: Int,
                isCurrentlyActive: Boolean
            ) {
                val itemView = viewHolder.itemView
                if (dX != 0f) {
                    c.drawRoundRect(
                        itemView.left.toFloat(),
                        itemView.top.toFloat(),
                        itemView.right.toFloat(),
                        itemView.bottom.toFloat(),
                        cornerRadius,
                        cornerRadius,
                        backgroundPaint
                    )
                    deleteIcon?.let { icon ->
                        val margin = (itemView.height - icon.intrinsicHeight) / 2
                        val iconTop = itemView.top + margin
                        val iconBottom = iconTop + icon.intrinsicHeight
                        if (dX > 0) {
                            val left = itemView.left + margin
                            icon.setBounds(left, iconTop, left + icon.intrinsicWidth, iconBottom)
                        } else {
                            val right = itemView.right - margin
                            icon.setBounds(right - icon.intrinsicWidth, iconTop, right, iconBottom)
                        }
                        icon.draw(c)
                    }
                }
                super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive)
            }
        }
        ItemTouchHelper(callback).attachToRecyclerView(binding.recyclerView)
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
