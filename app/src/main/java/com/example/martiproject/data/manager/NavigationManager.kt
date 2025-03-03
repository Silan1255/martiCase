package com.example.martiproject.data.manager

import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.widget.TextView
import android.widget.Toast
import com.example.martiproject.R
import com.example.martiproject.data.api.DirectionService
import com.example.martiproject.data.storage.RouteSharedPreferences
import com.example.martiproject.databinding.DialogMarkerLocationBinding
import com.example.martiproject.databinding.FragmentMapBinding
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.Polyline
import com.google.android.gms.maps.model.PolylineOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

class NavigationManager @Inject constructor(
    private val context: Context,
    private val googleMap: GoogleMap?,
    private val markerManager: MarkerManager,
    private val locationManager: LocationManager,
    private val directionsService: DirectionService,
    private val mapUIManager: MapUIManager,
    private val layoutInflater: LayoutInflater,
    private val routeSharedPreferences: RouteSharedPreferences
) {
    var isLocationPickingMode = false
    private var isNavigationActive = false
    private var navigationPolyline: Polyline? = null
    private var currentDestination: LatLng? = null

    init {
        runCatching {
            if (routeSharedPreferences.isNavigationActive()) {
                isNavigationActive = true
                currentDestination = routeSharedPreferences.getDestination()
            }
        }.onFailure { isNavigationActive = false }
    }

    fun toggleLocationPickingMode(binding: FragmentMapBinding) {
        isLocationPickingMode = !isLocationPickingMode
        binding.apply {
            btnPickLocation.text = context.getString(if (isLocationPickingMode) R.string.confirm_location else R.string.pick_location)
            ivMarkerCenter.visibility = if (isLocationPickingMode) android.view.View.VISIBLE else android.view.View.GONE
        }
        locationManager.showUserMarker(!isLocationPickingMode)
        if (!isNavigationActive) markerManager.clearPathMarkers()
        if (!isLocationPickingMode) googleMap?.cameraPosition?.target?.let { handleSelectedLocation(it) }
    }

    fun handleSelectedLocation(location: LatLng) {
        if (isNavigationActive) stopNavigation()
        markerManager.createDestinationMarker(location)
        currentDestination = location
        locationManager.getCurrentLocation()?.let {
            Handler(Looper.getMainLooper()).postDelayed({ showNavigationOptionsDialog(it, location) }, 500)
        }
    }

    private fun showNavigationOptionsDialog(origin: LatLng, destination: LatLng) {
        AlertDialog.Builder(context)
            .setTitle(context.getString(R.string.route_directions))
            .setMessage(context.getString(R.string.how_to_go))
            .setPositiveButton(context.getString(R.string.by_car)) { _, _ -> drawRoute(origin, destination, "driving") }
            .setNeutralButton(context.getString(R.string.by_walking)) { _, _ -> drawRoute(origin, destination, "walking") }
            .setNegativeButton(context.getString(R.string.cancel), null)
            .show()
    }

    private fun drawRoute(origin: LatLng, destination: LatLng, mode: String) {
        navigationPolyline?.remove()
        mapUIManager.showProgressBar(true)

        CoroutineScope(Dispatchers.Main).launch {
            runCatching {
                val directions = directionsService.getDirections(origin, destination, mode)
                mapUIManager.showProgressBar(false)

                if (directions.success && directions.routePoints.isNotEmpty()) {
                    navigationPolyline = googleMap?.addPolyline(
                        PolylineOptions().addAll(directions.routePoints)
                            .color(if (mode == "driving") Color.BLUE else Color.GREEN)
                            .width(12f)
                    )

                    val boundsBuilder = LatLngBounds.Builder()
                    directions.routePoints.forEach { boundsBuilder.include(it) }
                    googleMap?.animateCamera(CameraUpdateFactory.newLatLngBounds(boundsBuilder.build(), 100))

                    routeSharedPreferences.saveNavigationRoute(directions.routePoints)
                    routeSharedPreferences.saveNavigationMode(mode)
                    routeSharedPreferences.saveRouteInfo(directions.distance, directions.duration)

                    showRouteInfoAndStartButton(directions.distance, directions.duration, destination)
                }
            }.onFailure {
                mapUIManager.showProgressBar(false)
                Toast.makeText(context, context.getString(R.string.navigation_error, it.localizedMessage), Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun saveNavigationState() {
        if (isNavigationActive) {
            currentDestination?.let { destination ->
                routeSharedPreferences.saveNavigationState(navigationPolyline?.points ?: emptyList(), destination, true)
            }
        }
    }

    private fun showRouteInfoAndStartButton(distance: String, duration: String, destination: LatLng) {
        val binding = DialogMarkerLocationBinding.inflate(layoutInflater)
        binding.tvMarkerAddress.text = context.getString(R.string.distance_format, distance)
        binding.tvMarkerTitle.text = context.getString(R.string.duration_format, duration)

        AlertDialog.Builder(context)
            .setTitle(context.getString(R.string.route_info))
            .setView(binding.root)
            .setPositiveButton(context.getString(R.string.start_navigation)) { _, _ ->
                startNavigation(destination)
            }
            .setNegativeButton(context.getString(R.string.cancel), null)
            .show()
    }

    private fun startNavigation(destination: LatLng) {
        isNavigationActive = true
        currentDestination = destination

        routeSharedPreferences.setNavigationActive(true)
        routeSharedPreferences.saveDestination(destination)

        locationManager.startLocationTracking { markerManager.addPathMarker(it) }
        locationManager.setLocationListener { checkDestinationProximity(LatLng(it.latitude, it.longitude)) }

        mapUIManager.showNavigationUI { stopNavigation() }
        markerManager.createDestinationCircle(destination)
        saveNavigationState()
    }

    private fun checkDestinationProximity(currentLatLng: LatLng) {
        currentDestination?.let {
            if (locationManager.calculateDistance(currentLatLng.latitude, currentLatLng.longitude, it.latitude, it.longitude) <= 10) {
                showDestinationReachedDialog()
            }
        }
    }

    private fun showDestinationReachedDialog() {
        AlertDialog.Builder(context)
            .setTitle(context.getString(R.string.destination_reached))
            .setMessage(context.getString(R.string.destination_reached_message))
            .setPositiveButton(context.getString(R.string.ok)) { _, _ -> stopNavigation() }
            .show()
    }

    private fun stopNavigation() {
        isNavigationActive = false
        currentDestination = null
        routeSharedPreferences.setNavigationActive(false)
        markerManager.clearNavigationMarkers()
        markerManager.clearPathMarkers()
        markerManager.clearDestinationCircle()
        navigationPolyline?.remove()
        navigationPolyline = null
        locationManager.showUserMarker(true)
        mapUIManager.removeNavigationUI()
    }
}
