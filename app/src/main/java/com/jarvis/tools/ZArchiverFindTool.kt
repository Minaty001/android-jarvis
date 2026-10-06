package com.jarvis.tools

import android.content.Context
import android.content.Intent
import android.net.Uri
import java.io.File
import com.jarvis.files.FileManager
import com.jarvis.foundation.ParameterSchema
import com.jarvis.foundation.RiskLevel
import com.jarvis.foundation.ToolMetadata
import com.jarvis.tools.Tool
import com.jarvis.tools.ToolResult

class ZArchiverFindTool(
    private val context: Context,
    private val fileManager: FileManager = FileManager(context)
) : Tool {
    override val name: String = "FIND_IN_ZARCHIVER"
    override val description: String = "Searches for a specific file or folder and opens it directly in ZArchiver."
    
    val metadata = ToolMetadata(
        name = name,
        description = description,
        parameters = listOf(
            ParameterSchema("query", "string", "Name of the file or folder to find and open")
        ),
        riskLevel = RiskLevel.LOW
    )

    override suspend fun execute(params: Map<String, String>): ToolResult {
        val query = params["query"] ?: return ToolResult(false, "Search query missing")
        
        // Step 1: Search the file system
        val matches = fileManager.searchFiles(query, maxResults = 1)
        if (matches.isEmpty()) {
            return ToolResult(true, "Could not find any file or folder named '$query'")
        }
        
        val targetFile = File(matches.first().path)
        val isDirectory = targetFile.isDirectory
        
        // Step 2: Construct the explicit intent
        val pkgName = "ru.zdevs.zarchiver"
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setPackage(pkgName)
            val mimeType = if (isDirectory) "resource/folder" else "*/*"
            setDataAndType(Uri.parse("file://${targetFile.absolutePath}"), mimeType)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        
        // Step 3: Launch
        return try {
            val pm = context.packageManager
            if (intent.resolveActivity(pm) != null || isPackageInstalled(context, pkgName)) {
                context.startActivity(intent)
                ToolResult(true, "Successfully opened '${targetFile.name}' in ZArchiver.")
            } else {
                ToolResult(false, "ZArchiver is not installed on this device.")
            }
        } catch (e: Exception) {
            ToolResult(false, "Failed to open ZArchiver: ${e.message}")
        }
    }
    
    private fun isPackageInstalled(ctx: Context, pkgName: String): Boolean {
        return try {
            ctx.packageManager.getPackageInfo(pkgName, 0)
            true
        } catch (e: Exception) {
            false
        }
    }
}
