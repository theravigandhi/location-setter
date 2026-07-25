package com.locationsetter.app.ui.saved

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.locationsetter.app.R
import com.locationsetter.app.data.room.LocationEntity
import com.locationsetter.app.databinding.ItemSavedLocationBinding

class SavedLocationsAdapter(
    private val onUse: (LocationEntity) -> Unit,
    private val onRename: (LocationEntity) -> Unit,
    private val onDelete: (LocationEntity) -> Unit
) : ListAdapter<LocationEntity, SavedLocationsAdapter.ViewHolder>(DIFF_CALLBACK) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemSavedLocationBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(private val binding: ItemSavedLocationBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(location: LocationEntity) {
            binding.locationName.text = location.name
            binding.locationCoordinates.text = binding.root.context.getString(
                R.string.coordinates_format,
                location.latitude,
                location.longitude
            )
            binding.root.setOnClickListener { onUse(location) }
            binding.renameButton.setOnClickListener { onRename(location) }
            binding.deleteButton.setOnClickListener { onDelete(location) }
        }
    }

    companion object {
        private val DIFF_CALLBACK = object : DiffUtil.ItemCallback<LocationEntity>() {
            override fun areItemsTheSame(oldItem: LocationEntity, newItem: LocationEntity) =
                oldItem.id == newItem.id

            override fun areContentsTheSame(oldItem: LocationEntity, newItem: LocationEntity) =
                oldItem == newItem
        }
    }
}
