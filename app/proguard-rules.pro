# Jarvis — targeted R8 keep rules.
# Only what reflection / JNI genuinely needs is kept; everything else is shrunk.

# ---------------------------------------------------------------------------
# 1. sherpa-onnx (ASR + TTS) — pure JNI bridge.
#    libsherpa-onnx-jni.so resolves Kotlin classes, methods and fields by name,
#    so their names must survive obfuscation.
# ---------------------------------------------------------------------------
-keep class com.k2fsa.sherpa.onnx.** { *; }

# ---------------------------------------------------------------------------
# 2. TensorFlow Lite (wake-word + vision inference) — JNI bridge classes.
# ---------------------------------------------------------------------------
-keep class org.tensorflow.lite.** { *; }



# ---------------------------------------------------------------------------
# 4. OkHttp / Conscrypt optional platform providers — not present, just warn off.
# ---------------------------------------------------------------------------
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# ---------------------------------------------------------------------------
# 5. Coroutines debug probe artifact (never used at runtime in release).
# ---------------------------------------------------------------------------
-dontwarn kotlinx.coroutines.debug.**

# ---------------------------------------------------------------------------
# 6. Keep enum values()/valueOf() (default file has them; keep explicit for
#    enums persisted through Gson by ordinal/name).
# ---------------------------------------------------------------------------
-keepclassmembers enum com.jarvis.** {
    public static **[] values();
    public static ** valueOf(java.lang.String);
    <fields>;
}
-dontwarn org.osgi.framework.**
-dontwarn opennlp.**

# ---------------------------------------------------------------------------
# 7. LangChain4j SPI and Android PromptTemplateFactory override
# ---------------------------------------------------------------------------
-keep class dev.langchain4j.spi.** { *; }
-keep class com.jarvis.ai.AndroidPromptTemplateFactory { *; }
-keep interface com.jarvis.ai.Assistant { *; }
-keep class dev.langchain4j.** { *; }
-keep interface dev.langchain4j.** { *; }
-dontwarn dev.langchain4j.**
-keepattributes *Annotation*, Signature, InnerClasses, EnclosingMethod
