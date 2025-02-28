package com.example.cpen321app

import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.example.cpen321app.TaskViewModel.Companion
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.example.cpen321app.databinding.FragmentMapsBinding
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.PolygonOptions
import com.google.android.gms.maps.model.PolylineOptions
import com.google.maps.android.PolyUtil
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.Response
import org.json.JSONObject
import java.io.IOException
import kotlin.math.atan2

import com.example.cpen321app.TaskViewModel.Companion.server_ip
import com.example.cpen321app.TaskViewModel.Companion._taskList

class MapsFragment : Fragment(), OnMapReadyCallback {

    private var TAG = "MapsFragment"
    private lateinit var mMap: GoogleMap
    private var _binding: FragmentMapsBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMapsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        val mapFragment = childFragmentManager.findFragmentById(R.id.map) as? SupportMapFragment
        mapFragment?.getMapAsync(this)
    }

    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap

        mMap.uiSettings.isZoomControlsEnabled = true
        mMap.uiSettings.isZoomGesturesEnabled = true

        // Add a marker in Sydney and move the camera
        //        val sydney = LatLng(-34.0, 151.0)
        //        mMap.addMarker(MarkerOptions().position(sydney).title("Marker in Sydney"))
        //        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(sydney, 10f))

        update_taskListMarkers()

        val firstTask = _taskList.value?.firstOrNull()
        firstTask?.let {
            val firstTasklocation = LatLng(firstTask.location_lat, firstTask.location_lng)
            mMap.addMarker(MarkerOptions().position(firstTasklocation).title(firstTask.name))
            mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(firstTasklocation, 14f))
        }

    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun sendFetchGeofencesRequest(origin: LatLng, destination: LatLng) {
        val client = OkHttpClient()
        val url = "http://${server_ip}/fetchGeofences" // Replace with your server URL

        // Build JSON body
        val jsonBody = JSONObject().apply {
            put("origin", JSONObject().apply {
                put("latitude", origin.latitude)
                put("longitude", origin.longitude)
            })
            put("destination", JSONObject().apply {
                put("latitude", destination.latitude)
                put("longitude", destination.longitude)
            })
        }
        val mediaType = "application/json; charset=utf-8".toMediaTypeOrNull()
        val requestBody = RequestBody.create(mediaType, jsonBody.toString())

        val request = Request.Builder()
            .url(url)
            .post(requestBody)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e(TAG, "Failed to send fetchRoute request: ${e.message}")
            }

            override fun onResponse(call: Call, response: Response) {
                if (!response.isSuccessful) {
                    Log.e(TAG, "Unexpected response: ${response.message}")
                    return
                }
                response.body?.string()?.let { jsonResponse ->
                    Log.d(TAG, "Received response: $jsonResponse")
                    // Parse jsonResponse here and extract routes and polygon coordinates.
                    // For example:
                    val resultJson = JSONObject(jsonResponse)
                    val polygonJson = resultJson.getJSONArray("polygon")

                    val polygonPoints = mutableListOf<LatLng>()
                    for (i in 0 until polygonJson.length()) {
                        val pointObj = polygonJson.getJSONObject(i)
                        val lat = pointObj.getDouble("lat")
                        val lng = pointObj.getDouble("lng")
                        polygonPoints.add(LatLng(lat, lng))
                    }


                    requireActivity().runOnUiThread {
                        drawPolygon(polygonPoints);
                    }
                }
            }
        })
    }

    private val usedColors = mutableSetOf<Int>()

    private fun drawRoute(encodedPolyline: String, fiveMinPoint: LatLng) {
        val routeColor = getRandomColor()
        val polylineOptions = PolylineOptions()
            .addAll(PolyUtil.decode(encodedPolyline)) // Decode the polyline string
            .width(8f) // Set line width
            .color(routeColor)
            .geodesic(true)

        mMap.addPolyline(polylineOptions)

        val markerHue = getMarkerHue(routeColor)
        mMap.addMarker(
            MarkerOptions()
                .position(fiveMinPoint)
                .title("5-Minute Away Point")
                .icon(BitmapDescriptorFactory.defaultMarker(markerHue))
        )
    }

    private fun update_taskListMarkers(){
        for (item in _taskList.value!!) {
            drawMarker(LatLng(item.location_lat, item.location_lng), item.name)
        }
    }

    private fun drawMarker(point: LatLng, markerName: String){
        mMap.addMarker(
            MarkerOptions()
                .position(point)
                .title(markerName)
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED))
        )
    }

    private fun getMarkerHue(color: Int): Float {
        val hsv = FloatArray(3)
        Color.colorToHSV(color, hsv)
        return hsv[0]
    }

    private fun getRandomColor(): Int {
        val colorList = listOf(
            Color.GREEN, Color.BLUE, Color.YELLOW, Color.CYAN,
            Color.MAGENTA, Color.BLACK, Color.DKGRAY
        )

        if (usedColors.size >= colorList.size) {
            usedColors.clear()
        }

        val availableColors = colorList.filter { it !in usedColors }

        if (availableColors.isEmpty()) {
            return Color.BLACK
        }

        val newColor = availableColors.random()
        usedColors.add(newColor)
        return newColor
    }

    private fun drawPolygon(points: List<LatLng>) {
        Log.d(TAG, "drawPolygon: $points.size")
        if (points.size < 3) return

        val center = points.last()
        val sortedPoints = points.sortedBy { atan2(it.latitude - center.latitude, it.longitude - center.longitude) }

        val polygonOptions = PolygonOptions()
            .addAll(sortedPoints)
            .strokeColor(Color.RED)
            .fillColor(0x33FF0000)
            .strokeWidth(5f)

        mMap.addPolygon(polygonOptions)
    }
}
