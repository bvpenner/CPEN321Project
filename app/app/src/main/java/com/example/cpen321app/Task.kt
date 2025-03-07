package com.example.cpen321app

import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Polygon
import java.text.SimpleDateFormat
import java.util.*

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
    val description: String,
    var isCompleted: Boolean = false,
    var lastModified: Long = System.currentTimeMillis()
) {
    companion object {
        const val PRIORITY_HIGH = 3
        const val PRIORITY_MEDIUM = 2
        const val PRIORITY_LOW = 1

        private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
    }

    val location: LatLng
        get() = LatLng(location_lat, location_lng)

    val startDate: Date?
        get() = try {
            dateFormat.parse(start)
        } catch (e: Exception) {
            null
        }

    val endDate: Date?
        get() = try {
            dateFormat.parse(end)
        } catch (e: Exception) {
            null
        }

    val isOverdue: Boolean
        get() = endDate?.before(Date()) ?: false

    val timeUntilStart: Long
        get() = startDate?.time?.minus(System.currentTimeMillis()) ?: 0

    val priorityText: String
        get() = when (priority) {
            PRIORITY_HIGH -> "High"
            PRIORITY_MEDIUM -> "Medium"
            PRIORITY_LOW -> "Low"
            else -> "Unknown"
        }

    fun isWithinTimeRange(date: Date): Boolean {
        val taskStart = startDate
        val taskEnd = endDate
        return when {
            taskStart == null || taskEnd == null -> false
            else -> !date.before(taskStart) && !date.after(taskEnd)
        }
    }

    fun formatDuration(): String {
        val hours = duration.toInt()
        val minutes = ((duration - hours) * 60).toInt()
        return when {
            hours > 0 && minutes > 0 -> "${hours}h ${minutes}m"
            hours > 0 -> "${hours}h"
            minutes > 0 -> "${minutes}m"
            else -> "0m"
        }
    }
}

data class Geofence_Container(
    val id: String,
    val name: String,
    val currentPolygon: Polygon?,
    val coord_list: MutableList<LatLng>,
    val radius: Double = 100.0,  // Default radius in meters
    val expirationDuration: Long = 24 * 60 * 60 * 1000, // 24 hours in milliseconds
    val transitionTypes: Int = 0x1 | 0x2,  // GEOFENCE_TRANSITION_ENTER | GEOFENCE_TRANSITION_EXIT
    val loiteringDelay: Int = 60000 // 1 minute in milliseconds
) {
    fun getCenter(): LatLng {
        var latSum = 0.0
        var lngSum = 0.0
        coord_list.forEach { point ->
            latSum += point.latitude
            lngSum += point.longitude
        }
        return LatLng(latSum / coord_list.size, lngSum / coord_list.size)
    }

    fun containsPoint(point: LatLng): Boolean {
        // Ray casting algorithm for point-in-polygon test
        var inside = false
        var j = coord_list.size - 1
        for (i in coord_list.indices) {
            if ((coord_list[i].latitude > point.latitude) != (coord_list[j].latitude > point.latitude) &&
                point.longitude < (coord_list[j].longitude - coord_list[i].longitude) * 
                (point.latitude - coord_list[i].latitude) / 
                (coord_list[j].latitude - coord_list[i].latitude) + coord_list[i].longitude) {
                inside = !inside
            }
            j = i
        }
        return inside
    }
}
