with open("app/src/main/java/com/jarvis/voice/AndroidSpeechRecognizerEngine.kt", "r") as f:
    text = f.read()

text = text.replace(
    "speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {",
    "val ctx = context ?: return null\n                speechRecognizer = SpeechRecognizer.createSpeechRecognizer(ctx).apply {"
)

with open("app/src/main/java/com/jarvis/voice/AndroidSpeechRecognizerEngine.kt", "w") as f:
    f.write(text)
