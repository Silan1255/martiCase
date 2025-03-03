package com.example.martiproject.data.manager

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng

class CompassManager(context: Context) : SensorEventListener {
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val magnetometer = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)

    private val accelerometerReading = FloatArray(3)
    private val magnetometerReading = FloatArray(3)

    private val rotationMatrix = FloatArray(9)
    private val orientationAngles = FloatArray(3)

    private var bearing: Float = 0f
    private var isCompassEnabled = false

    private var googleMap: GoogleMap? = null
    private var lastLocation: Location? = null

    fun initialize(map: GoogleMap) {
        googleMap = map
    }

    fun startCompassTracking() {
        if (!isCompassEnabled) {
            sensorManager.registerListener(
                this,
                accelerometer,
                SensorManager.SENSOR_DELAY_GAME
            )
            sensorManager.registerListener(
                this,
                magnetometer,
                SensorManager.SENSOR_DELAY_GAME
            )
            isCompassEnabled = true
        }
    }

    fun stopCompassTracking() {
        if (isCompassEnabled) {
            sensorManager.unregisterListener(this)
            isCompassEnabled = false
        }
    }

    fun updateCameraWithBearing(location: Location) {
        val latLng = LatLng(location.latitude, location.longitude)
        val cameraPosition = CameraPosition.Builder()
            .target(latLng)
            .zoom(20f)
            .tilt(45f)
            .build()
        googleMap?.animateCamera(
            CameraUpdateFactory.newCameraPosition(cameraPosition),
            500,
            null
        )
    }

    private fun updateCamera() {
        lastLocation?.let { location ->
            val currentLatLng = LatLng(location.latitude, location.longitude)
            val cameraPosition = CameraPosition.Builder()
                .target(currentLatLng)
                .zoom(18f)
                .bearing(bearing)
                .tilt(60f)
                .build()

            googleMap?.animateCamera(
                CameraUpdateFactory.newCameraPosition(cameraPosition),
                500,
                null
            )
        }
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
            System.arraycopy(event.values, 0, accelerometerReading, 0, accelerometerReading.size)
        } else if (event.sensor.type == Sensor.TYPE_MAGNETIC_FIELD) {
            System.arraycopy(event.values, 0, magnetometerReading, 0, magnetometerReading.size)
        }

        SensorManager.getRotationMatrix(
            rotationMatrix,
            null,
            accelerometerReading,
            magnetometerReading
        )

        SensorManager.getOrientation(rotationMatrix, orientationAngles)

        val degrees = (Math.toDegrees(orientationAngles[0].toDouble()) + 360) % 360

        if (Math.abs(bearing - degrees) > 5) {
            bearing = degrees.toFloat()
            updateCamera()
        }
    }

    override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {
        // Sensör doğruluğu değiştiğinde yapılacak işlemler
    }

}