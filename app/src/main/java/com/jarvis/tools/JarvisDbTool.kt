package com.jarvis.tools

import android.content.Context
import com.jarvis.foundation.ParameterSchema
import com.jarvis.foundation.RiskLevel
import com.jarvis.foundation.ToolMetadata
import com.jarvis.storage.JarvisStorageHub
import java.io.File

class JarvisDbTool(
    private val context: Context? = null
) : Tool {

    override val name: String = "JARVIS_DB"
    override val description: String =
        "Inspects and queries the central local JARVIS storage hub at /storage/emulated/0/jarvis-db. Actions: 'stats' (shows database and storage stats), 'manifest' (reads manifest.json)."

    override val policy: ToolPolicy = ToolPolicy(
        idempotent = true,
        retryable = true,
        riskLevel = RiskLevel.LOW
    )

    val metadata = ToolMetadata(
        name = "JARVIS_DB",
        description = "Provides status and metrics over the central /storage/emulated/0/jarvis-db directory.",
        parameters = listOf(
            ParameterSchema("action", "string", "Action: stats, manifest", required = false)
        ),
        riskLevel = RiskLevel.LOW
    )

    override suspend fun execute(params: Map<String, String>): ToolResult {
        val ctx = context ?: return ToolResult.Success("Jarvis-DB simulation mode.")
        val rawAction = params["action"]?.trim()?.lowercase()
        val action = when {
            rawAction != null -> rawAction
            params.containsKey("manifest") -> "manifest"
            else -> "stats"
        }

        return when (action) {
            "stats", "status" -> {
                JarvisStorageHub.initStorage(ctx)
                val overview = JarvisStorageHub.getStorageOverview(ctx)
                val manifestFile = JarvisStorageHub.updateManifest(ctx)

                val dbDir = JarvisStorageHub.getDatabasesDirectory(ctx)
                val dbList = dbDir.listFiles()?.filter { it.name.endsWith(".db") }?.joinToString(", ") { it.name } ?: "none"

                val message = """
                    JARVIS Central Storage Hub Status:
                    • Location: ${overview["root_path"]}
                    • Total Databases: ${overview["total_databases"]} ($dbList)
                    • Total Reports: ${overview["total_reports"]}
                    • Hub Storage Used: ${overview["formatted_size"]}
                    • Manifest: ${manifestFile.name} (Updated)
                """.trimIndent()

                ToolResult.Success(
                    message = message,
                    data = mapOf(
                        "root_path" to overview["root_path"].toString(),
                        "databases" to dbList,
                        "reports_count" to overview["total_reports"].toString(),
                        "manifest_path" to manifestFile.absolutePath
                    )
                )
            }

            "manifest" -> {
                val root = JarvisStorageHub.getRootDirectory(ctx)
                val manifest = File(root, "jarvis_manifest.json")
                if (manifest.exists()) {
                    ToolResult.Success(manifest.readText(Charsets.UTF_8))
                } else {
                    JarvisStorageHub.initStorage(ctx)
                    ToolResult.Success("Manifest generated: ${JarvisStorageHub.updateManifest(ctx).absolutePath}")
                }
            }

            else -> ToolResult.Failed("Unknown action '$action'. Available actions: stats, manifest.")
        }
    }
}
