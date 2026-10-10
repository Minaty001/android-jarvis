package com.jarvis.iot

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Socket
import com.jarvis.memory.MemoryStore

/**
 * V5 IoT LAN Radar (Auto-discovery of smart devices).
 */
class IoTDiscoveryEngine(private val memoryStore: MemoryStore) {
    private val TAG = "IoTDiscovery"
    private val TIMEOUT_MS = 200 // Fast timeout for sweeping
    
    // Ports commonly used by smart home hubs/devices
    private val TARGET_PORTS = listOf(80, 8080, 8009, 8443)

    suspend fun sweepNetwork(baseIp: String = "192.168.1") = withContext(Dispatchers.IO) {
        Log.i(TAG, "Starting IoT Radar sweep on subnet: $baseIp.x")
        
        val discoveredDevices = mutableListOf<String>()
        
        // Sweep a subset of IPs for quick testing (e.g. 1 to 50)
        for (i in 1..50) {
            val ip = "$baseIp.$i"
            for (port in TARGET_PORTS) {
                if (isPortOpen(ip, port)) {
                    val deviceUrl = "http://$ip:$port"
                    Log.i(TAG, "IoT Device discovered at $deviceUrl")
                    discoveredDevices.add(deviceUrl)
                    // Save to memory store so LLM knows about it
                    memoryStore.saveMemory(
                        type = com.jarvis.memory.MemoryType.STRUCTURED_FACT,
                        key = "iot_device_${ip}_$port",
                        content = "IoT Device Discovered: $deviceUrl",
                        provenance = "IoTDiscoveryEngine"
                    )
                }
            }
        }
        
        if (discoveredDevices.isEmpty()) {
            Log.i(TAG, "IoT Radar sweep completed. No new devices found.")
        } else {
            Log.i(TAG, "IoT Radar sweep completed. Found ${discoveredDevices.size} devices.")
        }
    }
    
    private fun isPortOpen(ip: String, port: Int): Boolean {
        return try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(ip, port), TIMEOUT_MS)
                true
            }
        } catch (e: Exception) {
            false
        }
    }
}
