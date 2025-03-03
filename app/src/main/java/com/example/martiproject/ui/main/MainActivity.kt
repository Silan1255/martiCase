package com.example.martiproject.ui.main

import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import dagger.hilt.android.AndroidEntryPoint
import android.Manifest
import android.content.Intent
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.app.ActivityCompat
import com.example.martiproject.R
import com.example.martiproject.databinding.ActivityMainBinding
import com.example.martiproject.ui.map.MapContract
import com.example.martiproject.ui.map.MapFragment

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private val mapFragment = MapFragment()

    companion object {
        private val REQUIRED_PERMISSIONS = arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        @RequiresApi(Build.VERSION_CODES.Q)
        private val BACKGROUND_PERMISSION = arrayOf(
            Manifest.permission.ACCESS_BACKGROUND_LOCATION
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.container, mapFragment)
                .commit()
        }
        checkAndRequestPermissions()
        handleNotificationIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleNotificationIntent(intent)
    }

    private fun handleNotificationIntent(intent: Intent) {
        val fromNotification = intent.getBooleanExtra(getString(R.string.from_notification_key), false)
        val restoreNavigation = intent.getBooleanExtra(getString(R.string.restore_navigation_key), false)

        if (fromNotification && restoreNavigation) {
             mapFragment.restoreNavigationState()

        }
    }

    private fun checkAndRequestPermissions() {
        val permissionsToRequest = REQUIRED_PERMISSIONS.filter {
            ActivityCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }.toTypedArray()

        if (permissionsToRequest.isNotEmpty()) {
            ActivityCompat.requestPermissions(
                this,
                permissionsToRequest,
                MapContract.LOCATION_PERMISSION_REQUEST_CODE
            )
        } else {
            checkAndRequestBackgroundPermission()
        }
    }

    private fun checkAndRequestBackgroundPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.ACCESS_BACKGROUND_LOCATION
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                showBackgroundPermissionDialog()
            }
        }
    }

    private fun showBackgroundPermissionDialog() {
        android.app.AlertDialog.Builder(this)
            .setTitle(getString(R.string.background_permission_title))
            .setMessage(getString(R.string.background_permission_message))
            .setPositiveButton(getString(R.string.permission_grant)) { _, _ ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    ActivityCompat.requestPermissions(
                        this,
                        BACKGROUND_PERMISSION,
                        MapContract.LOCATION_PERMISSION_REQUEST_CODE + 1
                    )
                }
            }
            .setNegativeButton(getString(R.string.permission_later)) { dialog, _ -> dialog.dismiss() }
            .create()
            .show()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == MapContract.LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                checkAndRequestBackgroundPermission()
            }
        } else if (requestCode == MapContract.LOCATION_PERMISSION_REQUEST_CODE + 1) {
        }
    }
}
