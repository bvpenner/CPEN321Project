package com.example.cpen321app

import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Polygon

// Copilot generated
data class Task(
    val id: String,
    val name: String,
    val start: String,
    val end: String,
    var duration: Double,
    val location_lat: Double,
    val location_lng: Double,
    val priority: Int,
    val description: String
)

data class Geofence_Container(
    val id: String,
    val name: String,
    val currentPolygon: Polygon?,
    val coord_list: MutableList<LatLng>,
)
