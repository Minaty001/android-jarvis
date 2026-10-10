package com.jarvis.bluetooth

import android.bluetooth.BluetoothAdapter
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.util.Log
import java.util.concurrent.ConcurrentHashMap

/**
 * BLE Tracker Sentry Engine.
 * Detects persistent unfamiliar Bluetooth Low Energy beacons/trackers moving with the user
 * over multiple observation windows (e.g. Apple AirTags, Tile, Samsung SmartTags).
 */
class BleTrackerSentryEngine(private val context: Context? = null) {

    private val TAG = "BleTrackerSentry"

    data class ObservedBeacon(
        val macAddress: String,
        val deviceName: String?,
        val rssi: Int,
        val firstSeenTimestamp: Long,
        var lastSeenTimestamp: Long,
        var observationCount: Int
    )

    data class TrackerAlert(
        val macAddress: String,
        val name: String,
        val rssi: Int,
        val alertLevel: String,
        val details: String
    )

    // Map: MAC Address -> ObservedBeacon
    private val observedBeacons = ConcurrentHashMap<String, ObservedBeacon>()

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            result?.let { recordScan(it) }
        }

        override fun onBatchScanResults(results: MutableList<ScanResult>?) {
            results?.forEach { recordScan(it) }
        }

        override fun onScanFailed(errorCode: Int) {
            Log.w(TAG, "BLE Scan failed with code: $errorCode")
        }
    }

    private fun recordScan(result: ScanResult) {
        val device = result.device ?: return
        val address = device.address ?: return
        val name = device.name ?: "Unknown Beacon"
        val rssi = result.rssi
        val now = System.currentTimeMillis()

        val existing = observedBeacons[address]
        if (existing == null) {
            observedBeacons[address] = ObservedBeacon(
                macAddress = address,
                deviceName = name,
                rssi = rssi,
                firstSeenTimestamp = now,
                lastSeenTimestamp = now,
                observationCount = 1
            )
        } else {
            existing.lastSeenTimestamp = now
            existing.observationCount += 1
        }
    }

    fun startOpportunisticScan(): Boolean {
        return try {
            val adapter = BluetoothAdapter.getDefaultAdapter()
            if (adapter?.isEnabled == true) {
                val scanner = adapter.bluetoothLeScanner
                scanner?.startScan(scanCallback)
                Log.i(TAG, "BLE Tracker scanner active")
                true
            } else {
                Log.w(TAG, "Bluetooth is disabled, cannot start scan")
                false
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "Bluetooth scan permission not granted: ${e.message}")
            false
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start BLE scan: ${e.message}")
            false
        }
    }

    fun stopScan() {
        try {
            val adapter = BluetoothAdapter.getDefaultAdapter()
            if (adapter?.isEnabled == true) {
                adapter.bluetoothLeScanner?.stopScan(scanCallback)
                Log.i(TAG, "BLE Tracker scanner stopped")
            }
        } catch (_: Exception) {}
    }

    fun checkTrackerThreats(): List<TrackerAlert> {
        val now = System.currentTimeMillis()
        val alerts = mutableListOf<TrackerAlert>()

        observedBeacons.values.forEach { beacon ->
            val durationMinutes = (beacon.lastSeenTimestamp - beacon.firstSeenTimestamp) / (1000 * 60)
            // If beacon has been seen repeatedly (>3 times) over more than 5 minutes with strong signal
            if (beacon.observationCount >= 3 && durationMinutes >= 5 && beacon.rssi > -80) {
                alerts.add(
                    TrackerAlert(
                        macAddress = beacon.macAddress,
                        name = beacon.deviceName ?: "Unknown Tracker",
                        rssi = beacon.rssi,
                        alertLevel = "HIGH",
                        details = "Persistent tracker detected moving with you for $durationMinutes mins (${beacon.observationCount} sightings, RSSI ${beacon.rssi} dBm)."
                    )
                )
            }
        }

        return alerts
    }
}
