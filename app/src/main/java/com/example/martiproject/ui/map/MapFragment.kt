package com.example.martiproject.ui.map

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.example.martiproject.R
import com.example.martiproject.data.api.DirectionService
import com.example.martiproject.data.api.GoogleAddressService
import com.example.martiproject.data.manager.*
import com.example.martiproject.data.storage.RouteSharedPreferences
import com.example.martiproject.databinding.FragmentMapBinding
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.*
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MapFragment : Fragment(), OnMapReadyCallback {
    private lateinit var binding: FragmentMapBinding
    private var googleMap: GoogleMap? = null

    @Inject lateinit var googleAddressService: GoogleAddressService
    @Inject lateinit var directionsService: DirectionService
    @Inject lateinit var sharedPreferences: RouteSharedPreferences

    private var locationManager: LocationManager? = null
    private var navigationManager: NavigationManager? = null
    private var mapUIManager: MapUIManager? = null
    private var markerManager: MarkerManager? = null
    private var compassManager: CompassManager? = null
    private var isTracking = false
    private var pendingNavigationRestore = false

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        binding = FragmentMapBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        childFragmentManager.findFragmentById(R.id.map)
            ?.let { (it as SupportMapFragment).getMapAsync(this) }

        checkRestoreNavigation()
    }

    private fun checkRestoreNavigation() {
        pendingNavigationRestore = requireActivity().intent.getBooleanExtra("RESTORE_NAVIGATION", false)
    }

    override fun onMapReady(map: GoogleMap) {
        googleMap = map

        try {
            if (locationManager?.hasLocationPermission() == true) {
                googleMap?.apply {
                    isMyLocationEnabled = true
                    uiSettings.isMyLocationButtonEnabled = true
                }
            }
        } catch (e: SecurityException) {
        }

        initializeManagers()
        setupListeners()
        setupTrackingButtons()

        if (pendingNavigationRestore) restoreNavigationState()

        try {
            if (locationManager?.hasLocationPermission() == true) {
                locationManager?.showUserMarker(true)
                locationManager?.getCurrentLocation()
            } else {
                locationManager?.requestLocationPermission()
            }
        } catch (e: SecurityException) {
            locationManager?.requestLocationPermission()
        }
    }

    private fun initializeManagers() {
        markerManager = MarkerManager(requireContext(), googleMap, layoutInflater, googleAddressService)
        mapUIManager = MapUIManager(requireContext(), binding).apply {
            setupProgressBar()
        }
        locationManager = LocationManager(
            requireActivity(), requireContext(), googleMap, googleAddressService, sharedPreferences
        )
        navigationManager = NavigationManager(
            requireContext(), googleMap, markerManager!!, locationManager!!,
            directionsService, mapUIManager!!, layoutInflater, sharedPreferences
        )
        compassManager = CompassManager(requireContext()).apply {
            googleMap?.let { initialize(it) }
        }

        updateTrackingUI()
    }

    private fun setupListeners() {
        binding.btnPickLocation.setOnClickListener {
            navigationManager?.toggleLocationPickingMode(binding)
        }

        googleMap?.apply {
            mapUIManager?.configureMapSettings(this)
            markerManager?.setupMarkerInfoWindow(this)
            setOnMapClickListener { latLng ->
                if (navigationManager?.isLocationPickingMode == false) {
                    navigationManager?.handleSelectedLocation(latLng)
                }
            }
        }

        if (locationManager?.hasLocationPermission() == true) {
            enableLocationFeatures()
        } else {
            locationManager?.requestLocationPermission()
        }
    }

    private fun setupTrackingButtons() {
        binding.btnStartStopTracking.setOnClickListener { toggleTracking() }
        updateTrackingUI()
    }

    private fun toggleTracking() {
        isTracking = !isTracking
        if (isTracking) startTracking() else stopTracking()
        updateTrackingUI()
    }

    private fun updateTrackingUI() {
        binding.btnStartStopTracking.text = getString(
            if (isTracking) R.string.stop_tracking else R.string.start_tracking
        )
    }

    private fun enableLocationFeatures() {
        try {
            if (locationManager?.hasLocationPermission() == true) {
                googleMap?.apply {
                    isMyLocationEnabled = true
                    uiSettings.isMyLocationButtonEnabled = true
                }
                locationManager?.enableLocationFeatures()
            }
        } catch (e: SecurityException) {
            locationManager?.requestLocationPermission()
        }
    }

    private fun startTracking() {
        compassManager?.startCompassTracking()
        locationManager?.startLocationTracking { location ->
            compassManager?.updateCameraWithBearing(location)
        }
    }

    private fun stopTracking() {
        compassManager?.stopCompassTracking()
        locationManager?.stopLocationTracking()
    }

    fun restoreNavigationState() {
        if (googleMap == null || navigationManager == null) {
            pendingNavigationRestore = true
            return
        }
        if (sharedPreferences.isNavigationActive()) {
        } else {
            enableLocationFeatures()
        }

        pendingNavigationRestore = false
    }

     private fun addInitialUserLocationMarker() {
        try {
            if (locationManager?.hasLocationPermission() == true) {
                LocationServices.getFusedLocationProviderClient(requireContext())
                    .lastLocation
                    .addOnSuccessListener { location ->
                        location?.let {
                            markerManager?.addUserMarker(it)
                            val bearing = if (it.hasBearing()) it.bearing else 0f
                            val cameraPosition = CameraPosition.Builder()
                                .target(LatLng(it.latitude, it.longitude))
                                .zoom(15f)
                                .bearing(bearing)
                                .build()

                            googleMap?.animateCamera(CameraUpdateFactory.newCameraPosition(cameraPosition))
                        }
                    }
                    .addOnFailureListener { exception ->
                    }
            } else {
                locationManager?.requestLocationPermission()
            }
        } catch (e: SecurityException) {
            locationManager?.requestLocationPermission()
        }
    }

    override fun onResume() {
        super.onResume()
        googleMap?.let {
            if (locationManager?.hasLocationPermission() == true) {
                isTracking = false
                updateTrackingUI()
                restoreNavigationState()
                addInitialUserLocationMarker()
                locationManager?.getCurrentLocation()
            } else {
                locationManager?.requestLocationPermission()
            }
        }
    }

    override fun onPause() {
        super.onPause()
        compassManager?.stopCompassTracking()

        if (sharedPreferences.isNavigationActive()) {
            navigationManager?.saveNavigationState()
        }
        sharedPreferences.setTrackingActive(isTracking)
    }

    override fun onDestroy() {
        compassManager?.stopCompassTracking()
        locationManager?.unbindLocationService()
        super.onDestroy()
    }
}