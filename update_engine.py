with open("app/src/main/java/com/jarvis/voice/AndroidSpeechRecognizerEngine.kt", "r") as f:
    text = f.read()

text = text.replace("class AndroidSpeechRecognizerEngine(private val context: Context) {", "open class AndroidSpeechRecognizerEngine(private val context: Context? = null) {")
text = text.replace("val state: StateFlow<AsrState>", "open val state: StateFlow<AsrState>")
text = text.replace("val usesRawAudio: Boolean = false", "open val usesRawAudio: Boolean = false")
text = text.replace("suspend fun start(asrDir: File)", "open suspend fun start(asrDir: File)")
text = text.replace("suspend fun startListening(", "open suspend fun startListening(")
text = text.replace("suspend fun pushAudio(samples: ShortArray)", "open suspend fun pushAudio(samples: ShortArray)")
text = text.replace("suspend fun stopListening()", "open suspend fun stopListening()")
text = text.replace("suspend fun stop()", "open suspend fun stop()")

with open("app/src/main/java/com/jarvis/voice/AndroidSpeechRecognizerEngine.kt", "w") as f:
    f.write(text)
