package com.example.cpen321app

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.cpen321app.databinding.ActivityLocationSearchBinding
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.widget.AutocompleteSupportFragment
import com.google.android.libraries.places.widget.listener.PlaceSelectionListener
import com.google.android.gms.common.api.Status

class LocationSearchActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLocationSearchBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLocationSearchBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Initialize Places if not already initialized.
        if (!Places.isInitialized()) {
            Places.initialize(applicationContext, BuildConfig.MAPS_API_KEY)
        }

        val autocompleteFragment = supportFragmentManager
            .findFragmentById(R.id.autocomplete_fragment) as AutocompleteSupportFragment

        autocompleteFragment.setPlaceFields(listOf(Place.Field.ID, Place.Field.NAME, Place.Field.LAT_LNG))

        autocompleteFragment.setOnPlaceSelectedListener(object : PlaceSelectionListener {
            override fun onPlaceSelected(place: Place) {
                val latLng = place.latLng
                if (latLng != null) {
                    // Optionally, store the chosen location in SessionManager.
                    val location = android.location.Location("manual")
                    location.latitude = latLng.latitude
                    location.longitude = latLng.longitude
                    SessionManager.currentLocation = location

                    // Return the result.
                    val resultIntent = Intent().apply {
                        putExtra("latitude", latLng.latitude)
                        putExtra("longitude", latLng.longitude)
                        putExtra("placeName", place.name)
                    }
                    setResult(Activity.RESULT_OK, resultIntent)
                    finish()
                }
            }

            override fun onError(status: Status) {
                setResult(Activity.RESULT_CANCELED)
                finish()
            }
        })
    }
}
