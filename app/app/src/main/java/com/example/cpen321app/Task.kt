package com.example.cpen321app

import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Polygon

data class Task(
    val id: String,
    val name: String,
    val start: String,
    val end: String,
    var duration: Double,
    val location_lat: Double,
    val location_lng: Double,
    val priority: Int,
    val description: String,
    var isGeofenceEnabled: Boolean = false
) {
    companion object {
        const val PRIORITY_HIGH = 3
        const val PRIORITY_MEDIUM = 2
        const val PRIORITY_LOW = 1
    }
}

data class Geofence_Container(
    val id: String,
    val name: String,
    val currentPolygon: Polygon?,
    val coord_list: MutableList<LatLng>
)
