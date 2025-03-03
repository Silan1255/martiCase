package com.example.martiproject.data.model

import com.google.android.gms.maps.model.LatLng

data class DirectionsResult(
    val routePoints: List<LatLng>,
    val distance: String,
    val duration: String,
    val success: Boolean = true,
    val errorMessage: String? = null
)