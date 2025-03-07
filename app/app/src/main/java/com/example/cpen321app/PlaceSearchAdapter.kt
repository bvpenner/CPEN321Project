package com.example.cpen321app

import android.view.LayoutInflater
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.cpen321app.databinding.ItemPlaceSearchBinding
import com.google.android.libraries.places.api.model.AutocompletePrediction
import com.google.android.libraries.places.api.model.Place

class PlaceSearchAdapter(
    private val onPlaceSelected: (AutocompletePrediction) -> Unit
) : ListAdapter<AutocompletePrediction, PlaceSearchAdapter.PlaceViewHolder>(PlaceDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PlaceViewHolder {
        val binding = ItemPlaceSearchBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return PlaceViewHolder(binding)
    }

    override fun onBindViewHolder(holder: PlaceViewHolder, position: Int) {
        val place = getItem(position)
        holder.bind(place)
        
        // Apply animation
        holder.itemView.animation = AnimationUtils.loadAnimation(
            holder.itemView.context,
            R.anim.item_animation_fall_down
        )
    }

    inner class PlaceViewHolder(
        private val binding: ItemPlaceSearchBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        init {
            binding.root.setOnClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onPlaceSelected(getItem(position))
                }
            }
        }

        fun bind(place: AutocompletePrediction) {
            binding.apply {
                placeName.text = place.getPrimaryText(null)
                placeAddress.text = place.getSecondaryText(null)
                
                // Set place type icon
                val placeTypes = place.placeTypes
                placeIcon.setImageResource(
                    when {
                        placeTypes.contains(Place.Type.RESTAURANT) -> R.drawable.ic_restaurant
                        placeTypes.contains(Place.Type.SHOPPING_MALL) -> R.drawable.ic_shopping
                        placeTypes.contains(Place.Type.PARK) -> R.drawable.ic_park
                        placeTypes.contains(Place.Type.SCHOOL) -> R.drawable.ic_school
                        placeTypes.contains(Place.Type.HOSPITAL) -> R.drawable.ic_hospital
                        else -> R.drawable.ic_location
                    }
                )

                // Set distance if available
                place.distanceMeters?.let { distance ->
                    placeDistance.text = formatDistance(distance)
                    placeDistance.visibility = android.view.View.VISIBLE
                } ?: run {
                    placeDistance.visibility = android.view.View.GONE
                }
            }
        }

        private fun formatDistance(meters: Int): String {
            return when {
                meters < 1000 -> "$meters m"
                else -> String.format("%.1f km", meters / 1000.0)
            }
        }
    }

    private class PlaceDiffCallback : DiffUtil.ItemCallback<AutocompletePrediction>() {
        override fun areItemsTheSame(
            oldItem: AutocompletePrediction,
            newItem: AutocompletePrediction
        ): Boolean {
            return oldItem.placeId == newItem.placeId
        }

        override fun areContentsTheSame(
            oldItem: AutocompletePrediction,
            newItem: AutocompletePrediction
        ): Boolean {
            return oldItem.getFullText(null).toString() == newItem.getFullText(null).toString()
        }
    }
} 