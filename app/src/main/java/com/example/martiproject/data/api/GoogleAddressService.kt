package com.example.martiproject.data.api

import android.content.Context
import android.util.LruCache
import com.example.martiproject.R
import com.example.martiproject.ui.map.MapContract
import com.example.martiproject.ui.map.MapContract.CACHE_SIZE
import com.example.martiproject.ui.map.MapContract.GEOCODING_API_URL
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GoogleAddressService @Inject constructor(private val context: Context) {
    private val addressCache = LruCache<String, String>(CACHE_SIZE)

    suspend fun getAddress(location: LatLng): String {
        return withContext(Dispatchers.IO) {
            val key = "${location.latitude},${location.longitude}"
            addressCache.get(key)?.let { cachedAddress ->
                return@withContext cachedAddress
            }

            try {
                val urlStr = "$GEOCODING_API_URL?latlng=${location.latitude},${location.longitude}" +
                        "&key=${MapContract.API_KEY}&language=${context.getString(R.string.geocoding_language_code)}"

                val url = URL(urlStr)
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"

                val responseCode = connection.responseCode
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    val reader = connection.inputStream.bufferedReader()
                    val response = reader.use { it.readText() }

                    val address = parseResponse(response)

                    if (address.isNotEmpty()) {
                        addressCache.put(key, address)
                    }

                    return@withContext address
                } else {
                    return@withContext context.getString(R.string.address_not_found)
                }
            } catch (e: Exception) {
                return@withContext context.getString(R.string.could_not_get_address_info)
            }
        }
    }

    private fun parseResponse(json: String): String {
        try {
            val jsonObject = JSONObject(json)
            val status = jsonObject.getString(context.getString(R.string.status))

            if (status != context.getString(R.string.ok)) {
                return context.getString(R.string.address_not_found)
            }

            val results = jsonObject.getJSONArray(context.getString(R.string.results))
            if (results.length() == 0) {
                return context.getString(R.string.address_not_found)
            }
            val result = results.getJSONObject(0)
            return result.getString(context.getString(R.string.formatted_address))
        } catch (e: Exception) {
            return context.getString(R.string.error_processing_address)
        }
    }
}