package com.example.martiproject.data.manager

import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
import android.location.Location
import android.view.LayoutInflater
import android.view.View
import android.widget.Toast
import com.example.martiproject.R
import com.example.martiproject.data.api.GoogleAddressService
import com.example.martiproject.databinding.DialogMarkerLocationBinding
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.model.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MarkerManager(
    private val context: Context,
    private val googleMap: GoogleMap?,
    private val layoutInflater: LayoutInflater,
    private val googleAddressService: GoogleAddressService
) {
    private var destinationMarker: Marker? = null
    private var userMarker: Marker? = null
    private var destinationCircle: Circle? = null
    private var pathPolyline: Polyline? = null
    private val pathPoints = mutableListOf<LatLng>()
    private val pathMarkers = mutableListOf<Marker>()

    fun setupMarkerInfoWindow(map: GoogleMap) {
        map.apply {
            setOnMarkerClickListener { it.showInfoWindow(); true }
            setInfoWindowAdapter(object : GoogleMap.InfoWindowAdapter {
                override fun getInfoWindow(marker: Marker): View? = null
                override fun getInfoContents(marker: Marker): View {
                    val binding = DialogMarkerLocationBinding.inflate(layoutInflater)
                    binding.tvMarkerTitle.text = marker.title
                    binding.tvMarkerAddress.text = marker.snippet
                    return binding.root
                }
            })
            setOnInfoWindowClickListener { marker ->
                marker.snippet?.takeIf { it.isNotEmpty() && !it.contains(context.getString(R.string.address_loading)) }?.let {
                    showAddressDetailsDialog(marker.title ?: "", it)
                }
            }
        }
    }

    fun createDestinationMarker(location: LatLng): Marker? {
        destinationMarker?.remove()
        return googleMap?.addMarker(
            MarkerOptions()
                .position(location)
                .title(context.getString(R.string.destination_location))
                .snippet(context.getString(R.string.address_loading))
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED))
        )?.apply {
            destinationMarker = this
            fetchAddressForMarker(this)
        }
    }

    fun createDestinationCircle(location: LatLng, radiusMeters: Double = 30.0) {
        destinationCircle?.remove()
        destinationCircle = googleMap?.addCircle(
            CircleOptions()
                .center(location)
                .radius(radiusMeters)
                .strokeWidth(3f)
                .strokeColor(Color.RED)
                .fillColor(Color.argb(70, 255, 0, 0))
        )
    }

    fun clearDestinationCircle() {
        destinationCircle?.remove()
        destinationCircle = null
    }

    fun addUserMarker(location: Location) {
        val latLng = LatLng(location.latitude, location.longitude)
        updateUserMarker(latLng)
    }

    private fun updateUserMarker(location: LatLng) {
        if (userMarker == null) {
            userMarker = googleMap?.addMarker(
                MarkerOptions()
                    .position(location)
                    .title(context.getString(R.string.my_location))
                    .snippet(context.getString(R.string.address_loading))
                    .anchor(0.5f, 0.5f)
                    .zIndex(1.0f)
            )?.apply { fetchAddressForMarker(this) }
        } else {
            userMarker?.position = location
            fetchAddressForMarker(userMarker)
        }
    }

    fun addPathMarker(location: Location, address: String? = null) {
        val latLng = LatLng(location.latitude, location.longitude)
        pathPoints.add(latLng)
        googleMap?.addMarker(
            MarkerOptions()
                .position(latLng)
                .title(context.getString(R.string.path_point, pathMarkers.size + 1))
                .snippet(address ?: context.getString(R.string.address_loading))
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN))
        )?.apply {
            pathMarkers.add(this)
            if (address == null) fetchAddressForMarker(this)
        }
        updatePathPolyline()
    }

    private fun updatePathPolyline() {
        if (pathPoints.size < 2) return
        pathPolyline?.remove()
        pathPolyline = googleMap?.addPolyline(
            PolylineOptions().addAll(pathPoints).color(Color.GREEN).width(10f)
        )
    }

    fun clearPathMarkers() {
        pathMarkers.forEach { it.remove() }
        pathMarkers.clear()
        pathPolyline?.remove()
        pathPolyline = null
        pathPoints.clear()
    }

    private fun fetchAddressForMarker(marker: Marker?) {
        marker?.let { selectedMarker ->
            val position = selectedMarker.position
            CoroutineScope(Dispatchers.Main).launch {
                try {
                    val address = withContext(Dispatchers.IO) {
                        googleAddressService.getAddress(position)
                    }

                    selectedMarker.snippet = address

                    if (selectedMarker.isInfoWindowShown) {
                        selectedMarker.hideInfoWindow()
                        selectedMarker.showInfoWindow()
                    }
                } catch (e: Exception) {
                    selectedMarker.snippet = context.getString(R.string.address_not_available)
                    if (selectedMarker.isInfoWindowShown) {
                        selectedMarker.hideInfoWindow()
                        selectedMarker.showInfoWindow()
                    }
                }
            }
        }
    }

    private fun showAddressDetailsDialog(title: String, address: String) {
        AlertDialog.Builder(context)
            .setTitle(title)
            .setMessage(address)
            .setPositiveButton(context.getString(R.string.ok)) { dialog, _ -> dialog.dismiss() }
            .setNeutralButton(context.getString(R.string.copy)) { dialog, _ ->
                val clipboard =
                    context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText(context.getString(R.string.address), address))
                Toast.makeText(
                    context,
                    context.getString(R.string.address_copied),
                    Toast.LENGTH_SHORT
                )
                    .show()
                dialog.dismiss()
            }
            .show()
    }

    fun clearNavigationMarkers() {
        destinationMarker?.remove()
        destinationMarker = null
    }
}