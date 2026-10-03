with open("app/src/main/java/com/jarvis/files/FileManager.kt", "r") as f:
    text = f.read()

text = text.replace(
    """    fun formatBytes(bytes: Long): String {
        return context?.let { android.text.format.Formatter.formatShortFileSize(it, bytes) } ?: "${bytes / 1048576L} MB"
    }""",
    """    fun formatBytes(bytes: Long): String {
        return context?.let { android.text.format.Formatter.formatShortFileSize(it, bytes) }
            ?: run {
                val mb = bytes / (1024.0 * 1024.0)
                if (mb >= 1024) {
                    String.format(java.util.Locale.ROOT, "%.2f GB", mb / 1024.0)
                } else {
                    String.format(java.util.Locale.ROOT, "%.1f MB", mb)
                }
            }
    }"""
)

with open("app/src/main/java/com/jarvis/files/FileManager.kt", "w") as f:
    f.write(text)
