package com.jarvis.location

import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.util.Log
import com.jarvis.runtime.JarvisEvent
import com.jarvis.runtime.JarvisEventBus

class GeofenceEngine(private val context: Context) {
    private val TAG = "GeofenceEngine"
    private var locationManager: LocationManager? = null
    
    // Geofences: ID -> Pair<Location, RadiusMeters>
    private val activeGeofences = mutableMapOf<String, Pair<Location, Float>>()
    private val geofenceStates = mutableMapOf<String, Boolean>()
    
    private val locationListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            evaluateGeofences(location)
        }
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
        override fun onProviderEnabled(provider: String) {}
        override fun onProviderDisabled(provider: String) {}
    }

    fun start() {
        locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        
        if (context.checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            try {
                // Battery-efficient network provider updates
                locationManager?.requestLocationUpdates(
                    LocationManager.NETWORK_PROVIDER,
                    120_000L, // 2 minutes
                    100f,     // 100 meters
                    locationListener
                )
                Log.i(TAG, "GeofenceEngine started monitoring location.")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start location updates", e)
            }
        }
    }
    
    fun stop() {
        locationManager?.removeUpdates(locationListener)
    }

    fun addGeofence(id: String, latitude: Double, longitude: Double, radiusMeters: Float = 150f) {
        val target = Location("").apply {
            this.latitude = latitude
            this.longitude = longitude
        }
        activeGeofences[id] = Pair(target, radiusMeters)
    }

    private fun evaluateGeofences(currentLocation: Location) {
        for ((id, data) in activeGeofences) {
            val (targetLoc, radius) = data
            val distance = currentLocation.distanceTo(targetLoc)
            val isInside = distance <= radius
            val wasInside = geofenceStates[id] ?: false
            
            if (isInside && !wasInside) {
                geofenceStates[id] = true
                Log.i(TAG, "Entered geofence: $id (Distance: ${distance}m)")
                JarvisEventBus.shared.post(JarvisEvent.GeofenceTransition(id, true))
            } else if (!isInside && wasInside) {
                geofenceStates[id] = false
                Log.i(TAG, "Exited geofence: $id (Distance: ${distance}m)")
                JarvisEventBus.shared.post(JarvisEvent.GeofenceTransition(id, false))
            }
        }
    }
}
