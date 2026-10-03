import re

def fix_test_file(filepath):
    with open(filepath, "r") as f:
        content = f.read()
    content = content.replace("import com.jarvis.voice.AsrEngine", "import com.jarvis.voice.AndroidSpeechRecognizerEngine")
    content = re.sub(r"class (\w+) : AsrEngine", r"class \1 : AndroidSpeechRecognizerEngine()", content)
    
    # Add override keywords if missing for MockAsrEngine methods
    for prop in ["val state", "val usesRawAudio"]:
        content = re.sub(rf"(?<!override ){prop}", f"override {prop}", content)
    for method in ["suspend fun start", "suspend fun startListening", "suspend fun pushAudio", "suspend fun stopListening", "suspend fun stop"]:
        content = re.sub(rf"(?<!override ){method}", f"override {method}", content)

    with open(filepath, "w") as f:
        f.write(content)

fix_test_file("app/src/test/java/com/jarvis/voice/audio/VoiceRouterTest.kt")
fix_test_file("app/src/test/java/com/jarvis/voice/ListeningSystemFixesTest.kt")
fix_test_file("app/src/test/java/com/jarvis/voice/LiveConversationControllerTest.kt")
fix_test_file("app/src/test/java/com/jarvis/VoicePipelineOptimizationTest.kt")

