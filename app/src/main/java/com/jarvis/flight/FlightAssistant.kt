package com.jarvis.flight

import android.content.Context
import android.location.Location
import android.location.LocationManager
import android.Manifest
import android.content.pm.PackageManager
import com.jarvis.control.ActionIntent
import com.jarvis.control.CommandResult
import kotlin.math.*

data class Airport(val code: String, val name: String, val lat: Double, val lon: Double)

class FlightAssistant(private val context: Context) {

    // Simple localized database of major airports for prototype
    private val majorAirports = listOf(
        Airport("VIDP", "Indira Gandhi International, Delhi", 28.5562, 77.1000),
        Airport("VABB", "Chhatrapati Shivaji Maharaj, Mumbai", 19.0896, 72.8656),
        Airport("VOBL", "Kempegowda International, Bangalore", 13.1986, 77.7066),
        Airport("VOMM", "Chennai International", 12.9941, 80.1709),
        Airport("KJFK", "John F. Kennedy International, NY", 40.6413, -73.7781),
        Airport("EGLL", "Heathrow Airport, London", 51.4700, -0.4543),
        Airport("OMDB", "Dubai International", 25.2532, 55.3657)
    )

    suspend fun provideData(intent: ActionIntent): CommandResult {
        return when (intent.action) {
            "nearest_airport" -> handleNearestAirport()
            "flight_status" -> handleFlightStatus(intent)
            "altitude" -> handleAltitude()
            else -> CommandResult.Failure("Unknown flight assistant action: ${intent.action}")
        }
    }

    private fun handleNearestAirport(): CommandResult {
        val loc = gpsFix() ?: return CommandResult.Failure("Unable to get current GPS location.")
        
        var nearest: Airport? = null
        var minDistance = Double.MAX_VALUE

        for (airport in majorAirports) {
            val dist = haversine(loc.latitude, loc.longitude, airport.lat, airport.lon)
            if (dist < minDistance) {
                minDistance = dist
                nearest = airport
            }
        }

        return if (nearest != null) {
            val distMiles = (minDistance * 0.621371).toInt()
            CommandResult.SuccessWithMessage("The nearest major airport is ${nearest.name} (${nearest.code}), approximately $distMiles miles away.")
        } else {
            CommandResult.Failure("Could not calculate nearest airport.")
        }
    }

    private fun handleAltitude(): CommandResult {
        val loc = gpsFix() ?: return CommandResult.Failure("Unable to get current GPS location.")
        
        return if (loc.hasAltitude()) {
            val altitudeMeters = loc.altitude
            val altitudeFeet = (altitudeMeters * 3.28084).toInt()
            CommandResult.SuccessWithMessage("Your current altitude is $altitudeFeet feet.")
        } else {
            CommandResult.SuccessWithMessage("GPS fix obtained, but altitude data is currently unavailable.")
        }
    }

    private suspend fun handleFlightStatus(intent: ActionIntent): CommandResult {
        val flightQuery = intent.value as? String ?: "latest flight status"
        val apiKey = com.jarvis.BuildConfig.SERP_API_KEY
        
        if (apiKey.isBlank() || apiKey == "null") {
            return CommandResult.Failure("SERP API Key is not configured.")
        }

        return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val url = "https://serpapi.com/search.json?q=flight+status+${java.net.URLEncoder.encode(flightQuery, "UTF-8")}&api_key=$apiKey"
                
                val request = okhttp3.Request.Builder()
                    .url(url)
                    .build()

                okhttp3.OkHttpClient().newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@use CommandResult.Failure("Failed to fetch flight data.")
                    val bodyStr = response.body?.string() ?: return@use CommandResult.Failure("Empty response from SerpApi.")
                    val json = org.json.JSONObject(bodyStr)
                    
                    // Try to extract answer box or organic result
                    if (json.has("answer_box")) {
                        val answerBox = json.getJSONObject("answer_box")
                        val answer = answerBox.optString("snippet", answerBox.optString("title", "Found flight info, but couldn't parse the status."))
                        CommandResult.SuccessWithMessage("Flight Info: $answer")
                    } else if (json.has("organic_results") && json.getJSONArray("organic_results").length() > 0) {
                        val firstResult = json.getJSONArray("organic_results").getJSONObject(0)
                        val snippet = firstResult.optString("snippet", "No detailed snippet available.")
                        CommandResult.SuccessWithMessage("Flight Info: $snippet")
                    } else {
                        CommandResult.SuccessWithMessage("Could not find live status for $flightQuery.")
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                CommandResult.Failure("Error fetching flight status: ${e.message}")
            }
        }
    }

    private fun gpsFix(): Location? {
        if (context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return null
        }
        return try {
            val manager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
            val best = manager.getProviders(true).mapNotNull { provider ->
                try { manager.getLastKnownLocation(provider) } catch (e: SecurityException) { null }
            }.maxByOrNull { it.accuracy }
            best?.takeIf { System.currentTimeMillis() - it.time < 2 * 60 * 60 * 1000L }
        } catch (e: Exception) {
            null
        }
    }

    // Returns distance in kilometers
    private fun haversine(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val R = 6371.0 // Radius of earth in km
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2) * sin(dLon / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return R * c
    }
}
