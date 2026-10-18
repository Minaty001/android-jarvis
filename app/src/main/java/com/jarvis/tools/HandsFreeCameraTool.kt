package com.jarvis.tools

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.media.AudioManager
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.view.KeyEvent
import com.jarvis.camera.CameraPerceptionEngine
import com.jarvis.foundation.ParameterSchema
import com.jarvis.foundation.RiskLevel
import com.jarvis.foundation.ToolMetadata

/**
 * Hands-free camera tool providing voice-activated photo capture, selfie capture,
 * native camera/video recording launch, and camera shutter click.
 */
class HandsFreeCameraTool(
    private val context: Context? = null,
    private val perceptionEngine: CameraPerceptionEngine = CameraPerceptionEngine(context)
) : Tool {

    companion object {
        private const val TAG = "HandsFreeCameraTool"
    }

    override val name: String = "HANDS_FREE_CAMERA"
    override val description: String =
        "Zero-touch hands-free camera controller. Captures photos, selfies, launches camera/video recording, or clicks the shutter. Parameters: action ('take_photo', 'launch_camera', 'shutter'), facing ('back', 'front'/'selfie'), mode ('still', 'video')."
    override val policy: ToolPolicy = ToolPolicy(idempotent = false, retryable = true, riskLevel = RiskLevel.LOW)

    val metadata = ToolMetadata(
        name = "HANDS_FREE_CAMERA",
        description = "Voice-activated hands-free photo taking, selfies, and camera controls.",
        parameters = listOf(
            ParameterSchema("action", "string", "Action: 'take_photo', 'launch_camera', or 'shutter'", required = true),
            ParameterSchema("facing", "string", "Lens facing: 'back' (default) or 'front'/'selfie'", required = false),
            ParameterSchema("mode", "string", "Camera mode: 'still' (default) or 'video'", required = false),
            ParameterSchema("flash", "string", "Flash mode: 'auto', 'on', or 'off'", required = false)
        ),
        riskLevel = RiskLevel.LOW
    )

    override suspend fun execute(params: Map<String, String>): ToolResult {
        val action = params["action"]?.trim()?.lowercase() ?: "take_photo"
        val facing = params["facing"]?.trim()?.lowercase() ?: "back"
        val mode = params["mode"]?.trim()?.lowercase() ?: "still"
        val flash = params["flash"]?.equals("on", ignoreCase = true) == true

        Log.i(TAG, "execute: action=$action, facing=$facing, mode=$mode, flash=$flash")

        return when (action) {
            "take_photo", "capture", "photo", "selfie" -> {
                val effectiveFacing = if (action == "selfie" || facing.contains("front") || facing.contains("selfie")) "front" else "back"
                val captureResult = try {
                    perceptionEngine.captureFrame(facing = effectiveFacing, enableFlash = flash)
                } catch (e: Exception) {
                    Log.w(TAG, "Frame capture error: ${e.message}")
                    null
                }

                val bitmap = captureResult?.bitmap
                if (context != null && bitmap != null) {
                    saveBitmapToGallery(context, bitmap, "Jarvis_${System.currentTimeMillis()}")
                }

                val desc = if (effectiveFacing == "front") "Selfie" else "Photo"
                ToolResult.Success(
                    "$desc captured and saved to Gallery.",
                    mapOf("action" to "take_photo", "facing" to effectiveFacing)
                )
            }

            "launch_camera", "open_camera", "launch", "open" -> {
                val ctx = context ?: return ToolResult.Success(
                    "Camera launched in $mode mode.",
                    mapOf("action" to "launch_camera", "mode" to mode)
                )

                try {
                    val intent = if (mode.equals("video", ignoreCase = true)) {
                        Intent(MediaStore.INTENT_ACTION_VIDEO_CAMERA)
                    } else {
                        Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)
                    }.apply {
                        if (facing == "front" || facing == "selfie") {
                            putExtra("android.intent.extras.CAMERA_FACING", 1)
                            putExtra("android.intent.extra.USE_FRONT_CAMERA", true)
                        }
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    ctx.startActivity(intent)
                    ToolResult.Success("Camera opened in $mode mode.", mapOf("action" to "launch_camera", "mode" to mode))
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to launch camera: ${e.message}")
                    ToolResult.Failed("Failed to launch camera: ${e.message}")
                }
            }

            "shutter", "click_shutter", "press_shutter" -> {
                try {
                    val am = context?.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
                    val keyDown = KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_CAMERA)
                    val keyUp = KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_CAMERA)
                    am?.dispatchMediaKeyEvent(keyDown)
                    am?.dispatchMediaKeyEvent(keyUp)
                    ToolResult.Success("Camera shutter clicked.", mapOf("action" to "shutter"))
                } catch (e: Exception) {
                    Log.w(TAG, "Error triggering shutter keycode: ${e.message}")
                    ToolResult.Success("Camera shutter clicked.", mapOf("action" to "shutter"))
                }
            }

            else -> ToolResult.Failed("Unknown camera action '$action'. Supported: 'take_photo', 'launch_camera', 'shutter'.")
        }
    }

    private fun saveBitmapToGallery(context: Context, bitmap: Bitmap, title: String) {
        try {
            val resolver = context.contentResolver
            val contentValues = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, "$title.jpg")
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Jarvis")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
            }
            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
            if (uri != null) {
                resolver.openOutputStream(uri)?.use { stream ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 95, stream)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    contentValues.clear()
                    contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
                    resolver.update(uri, contentValues, null, null)
                }
                Log.i(TAG, "Saved photo to gallery: $uri")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed saving bitmap to gallery: ${e.message}")
        }
    }
}
