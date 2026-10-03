with open("app/src/main/java/com/jarvis/voice/AndroidSpeechRecognizerEngine.kt", "r") as f:
    text = f.read()

text = text.replace(
    "if (SpeechRecognizer.isRecognitionAvailable(context)) {",
    "if (context != null && SpeechRecognizer.isRecognitionAvailable(context)) {"
)

with open("app/src/main/java/com/jarvis/voice/AndroidSpeechRecognizerEngine.kt", "w") as f:
    f.write(text)
