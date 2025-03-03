package com.example.martiproject.data.storage

import android.content.Context
import android.content.SharedPreferences
import com.example.martiproject.enum.PreferenceKey
import com.google.android.gms.maps.model.LatLng
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RouteSharedPreferences @Inject constructor(context: Context) {

    private val sharedPreferences: SharedPreferences =
        context.getSharedPreferences(PreferenceKey.PREF_NAME.key, Context.MODE_PRIVATE)
    private val gson = Gson()

    fun isNavigationActive(): Boolean = sharedPreferences.getBoolean(PreferenceKey.NAVIGATION_ACTIVE.key, false)

    fun setNavigationActive(active: Boolean) =
        sharedPreferences.edit().putBoolean(PreferenceKey.NAVIGATION_ACTIVE.key, active).apply()

    fun setTrackingActive(active: Boolean) =
        sharedPreferences.edit().putBoolean(PreferenceKey.TRACKING_ACTIVE.key, active).apply()

    fun saveNavigationRoute(points: List<LatLng>) = sharedPreferences.edit()
        .putString(PreferenceKey.ROUTE_POINTS.key, gson.toJson(points))
        .apply()

    fun addRoutePoint(position: LatLng, address: String?) {
        val routePoints =
            getRoutePoints().toMutableList().apply { add(RoutePoint(position, address)) }
        sharedPreferences.edit().putString(PreferenceKey.ROUTE_POINTS.key, gson.toJson(routePoints)).apply()
    }

    fun getRoutePoints(): List<RoutePoint> {
        val pointsJson = sharedPreferences.getString(PreferenceKey.ROUTE_POINTS.key, null)
        return if (pointsJson != null) {
            val type = object : TypeToken<List<RoutePoint>>() {}.type
            gson.fromJson(pointsJson, type)
        } else {
            emptyList()
        }
    }

    data class RoutePoint(val position: LatLng, val address: String?)

    fun saveDestination(destination: LatLng) = sharedPreferences.edit()
        .putString(PreferenceKey.DESTINATION.key, gson.toJson(destination))
        .apply()

    fun getDestination(): LatLng? =
        sharedPreferences.getString(PreferenceKey.DESTINATION.key, null)?.let {
            try {
                gson.fromJson(it, LatLng::class.java)
            } catch (e: Exception) {
                null
            }
        }

    fun saveNavigationMode(mode: String) =
        sharedPreferences.edit().putString(PreferenceKey.NAVIGATION_MODE.key, mode).apply()

    fun saveRouteInfo(distance: String, duration: String) = sharedPreferences.edit()
        .putString(PreferenceKey.ROUTE_DISTANCE.key, distance)
        .putString(PreferenceKey.ROUTE_DURATION.key, duration)
        .apply()

    fun saveNavigationState(routePoints: List<LatLng>, destination: LatLng, isActive: Boolean) {
        setNavigationActive(isActive)
        saveDestination(destination)
        saveNavigationRoute(routePoints)
    }
}
