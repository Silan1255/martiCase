package com.example.martiproject.data.api

import android.content.Context
import com.example.martiproject.R
import com.example.martiproject.data.model.DirectionsResult
import com.example.martiproject.ui.map.MapContract.API_KEY
import com.example.martiproject.ui.map.MapContract.DIRECTIONS_API_URL
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DirectionService @Inject constructor(private val context: Context) {
    suspend fun getDirections(
        origin: LatLng,
        destination: LatLng,
        mode: String = "driving"
    ): DirectionsResult {
        return withContext(Dispatchers.IO) {
            try {
                val urlString = buildUrl(origin, destination, mode)
                val url = URL(urlString)
                val connection = url.openConnection() as HttpURLConnection

                try {
                    connection.requestMethod = "GET"

                    val responseCode = connection.responseCode
                    if (responseCode == HttpURLConnection.HTTP_OK) {
                        val reader = BufferedReader(InputStreamReader(connection.inputStream))
                        val response = StringBuilder()
                        var line: String?

                        while (reader.readLine().also { line = it } != null) {
                            response.append(line)
                        }

                        reader.close()
                        return@withContext parseResponse(response.toString())
                    } else {
                        return@withContext DirectionsResult(
                            emptyList(),
                            "",
                            "",
                            false,
                            context.getString(R.string.http_error_code, responseCode)
                        )
                    }
                } finally {
                    connection.disconnect()
                }
            } catch (e: Exception) {
                return@withContext DirectionsResult(
                    emptyList(),
                    "",
                    "",
                    false,
                    context.getString(R.string.error_getting_directions, e.localizedMessage)
                )
            }
        }
    }

    private fun buildUrl(origin: LatLng, destination: LatLng, mode: String): String {
        return "$DIRECTIONS_API_URL?" +
                "origin=${origin.latitude},${origin.longitude}" +
                "&destination=${destination.latitude},${destination.longitude}" +
                "&mode=$mode" +
                "&language=tr" +
                "&key=$API_KEY"
    }

    private fun parseResponse(json: String): DirectionsResult {
        try {
            val jsonObject = JSONObject(json)
            val status = jsonObject.getString("status")

            if (status != "OK") {
                return DirectionsResult(
                    emptyList(),
                    "",
                    "",
                    false,
                    context.getString(R.string.api_response_status, status)
                )
            }

            val routes = jsonObject.getJSONArray("routes")
            if (routes.length() == 0) {
                return DirectionsResult(
                    emptyList(),
                    "",
                    "",
                    false,
                    context.getString(R.string.no_routes_found)
                )
            }

            val route = routes.getJSONObject(0)
            val legs = route.getJSONArray("legs")
            val leg = legs.getJSONObject(0)

            val distance = leg.getJSONObject("distance").getString("text")
            val duration = leg.getJSONObject("duration").getString("text")

            val routePoints = mutableListOf<LatLng>()
            val steps = leg.getJSONArray("steps")

            for (i in 0 until steps.length()) {
                val step = steps.getJSONObject(i)
                val startLocation = step.getJSONObject("start_location")
                val endLocation = step.getJSONObject("end_location")

                val startLatLng = LatLng(
                    startLocation.getDouble("lat"),
                    startLocation.getDouble("lng")
                )
                val endLatLng = LatLng(
                    endLocation.getDouble("lat"),
                    endLocation.getDouble("lng")
                )

                val polyline = step.getJSONObject("polyline").getString("points")
                val decodedPolylinePoints = decodePolyline(polyline)

                if (routePoints.isEmpty()) {
                    routePoints.add(startLatLng)
                }

                routePoints.addAll(decodedPolylinePoints)
                routePoints.add(endLatLng)
            }

            return DirectionsResult(routePoints, distance, duration)
        } catch (e: Exception) {
            return DirectionsResult(
                emptyList(),
                "",
                "",
                false,
                context.getString(R.string.json_parsing_error, e.localizedMessage)
            )
        }
    }

    private fun decodePolyline(encoded: String): List<LatLng> {
        val poly = ArrayList<LatLng>()
        var index = 0
        val len = encoded.length
        var lat = 0
        var lng = 0

        while (index < len) {
            var b: Int
            var shift = 0
            var result = 0
            do {
                b = encoded[index++].toInt() - 63
                result = result or (b and 0x1f shl shift)
                shift += 5
            } while (b >= 0x20)
            val dlat = if (result and 1 != 0) (result shr 1).inv() else result shr 1
            lat += dlat

            shift = 0
            result = 0
            do {
                b = encoded[index++].toInt() - 63
                result = result or (b and 0x1f shl shift)
                shift += 5
            } while (b >= 0x20)
            val dlng = if (result and 1 != 0) (result shr 1).inv() else result shr 1
            lng += dlng

            val p = LatLng(lat.toDouble() / 1E5, lng.toDouble() / 1E5)
            poly.add(p)
        }

        return poly
    }
}