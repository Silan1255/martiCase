package com.example.martiproject.data.manager

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.os.Looper
import android.view.LayoutInflater
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.example.martiproject.R
import com.example.martiproject.data.api.GoogleAddressService
import com.example.martiproject.data.api.LocationService
import com.example.martiproject.data.storage.RouteSharedPreferences
import com.example.martiproject.ui.map.MapContract.LOCATION_PERMISSION_REQUEST_CODE
import com.example.martiproject.ui.map.MapContract.MIN_DISTANCE_TO_ADD_MARKER
import com.google.android.gms.location.*
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

class LocationManager @Inject constructor(
    private val activity: Activity,
    private val context: Context,
    private val googleMap: GoogleMap?,
    private val googleAddressService: GoogleAddressService,
    private val sharedPreferences: RouteSharedPreferences
) {
    private val fusedLocationClient: FusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(context)
    private var locationCallback: LocationCallback? = null
    private var currentLocation: LatLng? = null
    private var locationUpdateListener: ((Location) -> Unit)? = null
    private var isTracking = false
    private var lastMarkerLocation: Location? = null
    private var locationTrackingServiceIntent: Intent? = null

    fun hasLocationPermission(): Boolean {
        return ActivityCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun requestLocationPermission() {
        ActivityCompat.requestPermissions(
            activity,
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
            LOCATION_PERMISSION_REQUEST_CODE
        )
    }

    fun getCurrentLocation(): LatLng? {
        if (hasLocationPermission()) {
            try {
                fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                    location?.let {
                        currentLocation = LatLng(it.latitude, it.longitude)
                        moveCameraToLocation(currentLocation!!)
                    }
                }
            } catch (e: SecurityException) {
            }
        }
        return currentLocation
    }

    private fun moveCameraToLocation(location: LatLng) {
        googleMap?.animateCamera(
            CameraUpdateFactory.newLatLngZoom(location, 15f)
        )
    }

    fun showUserMarker(show: Boolean) {
        if (hasLocationPermission()) {
            try {
                googleMap?.isMyLocationEnabled = show
            } catch (e: SecurityException) {
            }
        }
    }

    fun enableLocationFeatures() {
        if (hasLocationPermission()) {
            try {
                googleMap?.isMyLocationEnabled = true
                googleMap?.uiSettings?.isMyLocationButtonEnabled = true
                loadSavedRoute()
                getCurrentLocation()
            } catch (e: SecurityException) {
            }
        }
    }

    fun startLocationTracking(onLocationUpdate: ((Location) -> Unit)? = null) {
        if (!hasLocationPermission()) {
            requestLocationPermission()
            return
        }
        isTracking = true
        sharedPreferences.setTrackingActive(true)
        startLocationService()
        locationUpdateListener = onLocationUpdate

        if (locationCallback == null) {
            locationCallback = object : LocationCallback() {
                override fun onLocationResult(locationResult: LocationResult) {
                    super.onLocationResult(locationResult)
                    locationResult.lastLocation?.let { location ->
                        currentLocation = LatLng(location.latitude, location.longitude)

                        if (shouldAddNewMarker(location)) {
                            lastMarkerLocation = location
                            locationUpdateListener?.invoke(location)
                            saveLocationToRoute(location)
                        } else {
                            locationUpdateListener?.invoke(location)
                        }
                    }
                }
            }
        }

        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5000)
            .setMinUpdateIntervalMillis(3000)
            .build()

        try {
            if (hasLocationPermission()) {
                fusedLocationClient.requestLocationUpdates(
                    locationRequest,
                    locationCallback as LocationCallback,
                    Looper.getMainLooper()
                )
            }
        } catch (e: SecurityException) {
        }
    }

    fun stopLocationTracking() {
        isTracking = false
        sharedPreferences.setTrackingActive(false)
        locationCallback?.let { fusedLocationClient.removeLocationUpdates(it) }
        stopLocationService()
    }

    private fun shouldAddNewMarker(newLocation: Location): Boolean {
        val lastLocation = lastMarkerLocation ?: return true

        val distance = calculateDistance(
            lastLocation.latitude, lastLocation.longitude,
            newLocation.latitude, newLocation.longitude
        )
        return distance >= MIN_DISTANCE_TO_ADD_MARKER
    }

    fun calculateDistance(
        lat1: Double, lon1: Double,
        lat2: Double, lon2: Double
    ): Float {
        val results = FloatArray(1)
        Location.distanceBetween(lat1, lon1, lat2, lon2, results)
        return results[0]
    }

    fun setLocationListener(listener: (Location) -> Unit) {
        locationUpdateListener = listener
    }

    private fun startLocationService() {
        if (locationTrackingServiceIntent == null) {
            locationTrackingServiceIntent = Intent(context, LocationService::class.java)
            context.startService(locationTrackingServiceIntent)
        }
    }

    private fun stopLocationService() {
        locationTrackingServiceIntent?.let {
            context.stopService(it)
            locationTrackingServiceIntent = null
        }
    }

    fun unbindLocationService() {
        stopLocationTracking()
    }

    private fun saveLocationToRoute(location: Location) {
        CoroutineScope(Dispatchers.IO).launch {
            val latLng = LatLng(location.latitude, location.longitude)
            val address = try {
                googleAddressService.getAddress(latLng)
            } catch (e: Exception) {
                context.getString(
                    R.string.location_coordinates,
                    location.latitude,
                    location.longitude
                )
            }
            sharedPreferences.addRoutePoint(latLng, address)
        }
    }

    private fun loadSavedRoute() {
        CoroutineScope(Dispatchers.Main).launch {
            try {
                val routePoints = sharedPreferences.getRoutePoints()

                if (routePoints.isNotEmpty()) {
                    val markerMgr = MarkerManager(
                        context,
                        googleMap,
                        (activity as? AppCompatActivity)?.layoutInflater ?: LayoutInflater.from(context),
                        googleAddressService
                    )
                    markerMgr.clearPathMarkers()

                    for (point in routePoints) {
                        val location = Location(context.getString(R.string.saved_location_provider)).apply {
                            latitude = point.position.latitude
                            longitude = point.position.longitude
                        }
                        markerMgr.addPathMarker(location, point.address)
                    }

                    if (routePoints.isNotEmpty()) {
                        val firstPoint = routePoints[0].position
                        googleMap?.animateCamera(CameraUpdateFactory.newLatLngZoom(
                            firstPoint,
                            15F
                        ))
                    }
                }
            } catch (e: Exception) {
            }
        }
    }
}