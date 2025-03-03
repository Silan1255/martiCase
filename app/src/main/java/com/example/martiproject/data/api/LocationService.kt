package com.example.martiproject.data.api

import android.app.*
import android.content.Intent
import android.content.Context
import android.content.pm.ServiceInfo
import android.location.Location
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import com.example.martiproject.R
import com.example.martiproject.data.storage.RouteSharedPreferences
import com.example.martiproject.ui.main.MainActivity
import com.example.martiproject.ui.map.MapContract.CHANNEL_ID
import com.example.martiproject.ui.map.MapContract.MIN_DISTANCE_TO_ADD_MARKER
import com.example.martiproject.ui.map.MapContract.NOTIFICATION_ID
import com.google.android.gms.location.*
import com.google.android.gms.maps.model.LatLng
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class LocationService : Service() {
    @Inject
    lateinit var routeSharedPreferences: RouteSharedPreferences

    private val binder = LocalBinder()
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private var lastLocation: Location? = null

    inner class LocalBinder : Binder() {
        fun getService(): LocationService = this@LocationService
    }

    override fun onCreate() {
        super.onCreate()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        createLocationCallback()
        createNotificationChannel()
        startForeground()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startLocationUpdates()
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder {
        return binder
    }

    override fun onDestroy() {
        super.onDestroy()
        stopLocationUpdates()
    }

    private fun createLocationCallback() {
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                super.onLocationResult(locationResult)
                locationResult.lastLocation?.let { location ->
                    if (shouldAddNewMarker(location)) {
                        lastLocation = location
                        CoroutineScope(Dispatchers.IO).launch {
                            routeSharedPreferences.addRoutePoint(
                                LatLng(location.latitude, location.longitude),
                                getString(R.string.location_coordinates, location.latitude, location.longitude)
                            )
                        }
                    }
                }
            }
        }
    }

    private fun shouldAddNewMarker(newLocation: Location): Boolean {
        val lastLoc = lastLocation ?: return true

        val results = FloatArray(1)
        Location.distanceBetween(
            lastLoc.latitude, lastLoc.longitude,
            newLocation.latitude, newLocation.longitude,
            results
        )

        return results[0] >= MIN_DISTANCE_TO_ADD_MARKER
    }

    private fun startLocationUpdates() {
        val locationRequest = LocationRequest.create().apply {
            interval = 10000
            fastestInterval = 5000
            priority = LocationRequest.PRIORITY_HIGH_ACCURACY
        }

        try {
            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                Looper.getMainLooper()
            )
        } catch (e: SecurityException) {
            e.printStackTrace()
        }
    }

    private fun stopLocationUpdates() {
        fusedLocationClient.removeLocationUpdates(locationCallback)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.location_tracking),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.location_tracking_notifications)
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun startForeground() {
        val notificationIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(getString(R.string.from_notification_key), true)

            if (routeSharedPreferences.isNavigationActive()) {
                putExtra(getString(R.string.restore_navigation_key), true)
            }
        }

        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = if (routeSharedPreferences.isNavigationActive()) {
            getString(R.string.navigation_active)
        } else {
            getString(R.string.tracking_location)
        }

        val text = if (routeSharedPreferences.isNavigationActive()) {
            getString(R.string.navigation_description)
        } else {
            getString(R.string.tracking_description)
        }

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }
}