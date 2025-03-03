package com.example.martiproject.data.manager

import android.content.Context
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ProgressBar
import androidx.core.content.ContextCompat
import com.example.martiproject.R
import com.example.martiproject.databinding.FragmentMapBinding
import com.google.android.gms.maps.GoogleMap

class MapUIManager(
    private val context: Context,
    private val binding: FragmentMapBinding,
) {
    private lateinit var progressBar: ProgressBar
    private var stopNavigationButton: Button? = null

    fun setupProgressBar() {
        progressBar = ProgressBar(context).apply {
            isIndeterminate = true
            visibility = View.GONE
        }
        binding.root.addView(progressBar, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.CENTER
        ))
    }

    fun showProgressBar(show: Boolean) {
        progressBar.visibility = if (show) View.VISIBLE else View.GONE
    }

    fun configureMapSettings(map: GoogleMap) {
        map.uiSettings.apply {
            isZoomControlsEnabled = true
            isCompassEnabled = true
            isMyLocationButtonEnabled = false
        }
    }

    fun showNavigationUI(onStopNavigationClick: () -> Unit) {
        removeNavigationUI()
        stopNavigationButton = createButton(
            context.getString(R.string.stop_navigation),
            R.color.blue,
            onStopNavigationClick
        )
        binding.root.addView(stopNavigationButton, buttonLayoutParams(Gravity.TOP or Gravity.END))
    }

    fun removeNavigationUI() {
        stopNavigationButton?.let {
            (it.parent as? ViewGroup)?.removeView(it)
            stopNavigationButton = null
        }
    }

    private fun createButton(text: String, color: Int, onClick: () -> Unit): Button {
        return Button(context).apply {
            this.text = text
            setBackgroundColor(ContextCompat.getColor(context, color))
            setTextColor(ContextCompat.getColor(context, R.color.button_text))
            setOnClickListener { onClick() }
        }
    }

    private fun buttonLayoutParams(gravity: Int) = FrameLayout.LayoutParams(
        FrameLayout.LayoutParams.WRAP_CONTENT,
        FrameLayout.LayoutParams.WRAP_CONTENT
    ).apply {
        this.gravity = gravity
        val margin = context.resources.getDimensionPixelSize(R.dimen.margin_16)
        setMargins(margin, margin, margin, margin)
    }
}