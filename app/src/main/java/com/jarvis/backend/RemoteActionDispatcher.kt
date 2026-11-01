package com.jarvis.backend

import android.util.Log
import com.jarvis.tools.ToolExecutor
import com.jarvis.tools.ToolResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class RemoteActionDispatcher(
    private val toolExecutor: ToolExecutor,
    private val backendClient: JarvisBackendClient,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default),
    private val onPreambleSpoken: ((String) -> Unit)? = null
) {
    companion object {
        private const val TAG = "RemoteActionDispatcher"
    }

    init {
        backendClient.onActionReceived = { request ->
            handleRemoteAction(request)
        }
    }

    suspend fun handleRemoteAction(request: ServerEvent.ActionRequest) {
        Log.i(TAG, "Dispatching remote hardware action: ${request.action} (id=${request.actionId})")

        // Speak preamble if provided (e.g. "Calling Rahul now...")
        if (!request.speechPreamble.isNullOrBlank()) {
            onPreambleSpoken?.invoke(request.speechPreamble)
        }

        try {
            // Execute the action through the app's unified ToolExecutor
            val result: ToolResult = toolExecutor.execute(
                actionType = request.action,
                params = request.params,
                userApprovalGranted = true
            )

            Log.i(TAG, "Action ${request.actionId} finished: success=${result.success}")

            backendClient.sendActionResult(
                actionId = request.actionId,
                success = result.success,
                output = result.message,
                errorMessage = if (!result.success) result.message else null
            )
        } catch (e: Exception) {
            Log.e(TAG, "Exception executing remote action ${request.action}: ${e.message}", e)
            backendClient.sendActionResult(
                actionId = request.actionId,
                success = false,
                output = "Error: ${e.message}",
                errorMessage = e.message
            )
        }
    }
}
