import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val localProperties = Properties()
val localPropertiesFile = rootProject.file("local.properties")
if (localPropertiesFile.exists()) {
    localPropertiesFile.inputStream().use(localProperties::load)
}

fun propertyOrEnv(name: String, defaultValue: String = ""): String {
    return providers.gradleProperty(name).orNull
        ?: localProperties.getProperty(name)
        ?: System.getenv(name)
        ?: defaultValue
}

fun String.escapeForBuildConfig(): String =
    replace("\\", "\\\\").replace("\"", "\\\"")

val groqApiKey = propertyOrEnv("GROQ_API_KEY")
val groqModel = propertyOrEnv("GROQ_MODEL", "openai/gpt-oss-20b")
val geminiApiKey = propertyOrEnv("GEMINI_API_KEY")
val openRouterApiKey = propertyOrEnv("OPENROUTER_API_KEY")
val serpApiKey = propertyOrEnv("SERP_API_KEY")

android {
    namespace = "com.jarvis"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.jarvis"
        minSdk = 28
        targetSdk = 35
        versionCode = 2
        versionName = "1.1"

        // ABI optimization: keep only the ABIs used by real Android devices.
        // x86 / x86_64 are emulator-only architectures (no real phones/tablets);
        // they contributed ~81 MB of redundant native code to the universal APK.
        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a")
        }

        buildConfigField("String", "GROQ_API_KEY", "\"${groqApiKey.escapeForBuildConfig()}\"")
        buildConfigField("String", "GROQ_MODEL", "\"${groqModel.escapeForBuildConfig()}\"")
        buildConfigField("String", "GEMINI_API_KEY", "\"${geminiApiKey.escapeForBuildConfig()}\"")
        buildConfigField("String", "OPENROUTER_API_KEY", "\"${openRouterApiKey.escapeForBuildConfig()}\"")
        buildConfigField("String", "SERP_API_KEY", "\"${serpApiKey.escapeForBuildConfig()}\"")
    }

    buildFeatures {
        buildConfig = true
    }

    buildTypes {
        release {
            // R8 code shrinking + resource shrinking for the smallest installable APK.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    androidResources {
        noCompress += "tflite"
    }

    packaging {
        jniLibs {
            // Compress native libraries inside the APK to minimize download size.
            // (Libs are extracted to disk at install time — fully functional on all API levels.)
            useLegacyPackaging = true
        }
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.activity:activity-ktx:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("com.google.android.material:material:1.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.1")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.tensorflow:tensorflow-lite:2.14.0")


    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20231013")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.1")

    // Sherpa-ONNX for Speaker Verification
    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.aar"))))
}
