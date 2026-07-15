import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

// Release signing is read from android/keystore.properties, which is NOT in git.
// When it's absent (e.g. CI), release builds are simply left unsigned.
val keystorePropsFile = rootProject.file("keystore.properties")
val keystoreProps = Properties().apply {
    if (keystorePropsFile.exists()) keystorePropsFile.inputStream().use { load(it) }
}

// Public release is deliberately blocked until these external obligations have
// written evidence. Debug builds remain available for engineering and research.
val dataRightsCleared = providers.gradleProperty("dataRightsCleared")
    .map { it.toBoolean() }
    .getOrElse(false)
val clinicalValidationComplete = providers.gradleProperty("clinicalValidationComplete")
    .map { it.toBoolean() }
    .getOrElse(false)
val modelBaseUrl = providers.gradleProperty("modelBaseUrl")
    .orElse(providers.environmentVariable("SUNNY_MODEL_BASE_URL"))
    .getOrElse("")
    .trim()
check(modelBaseUrl.isEmpty() || (modelBaseUrl.startsWith("https://") && modelBaseUrl.endsWith("/"))) {
    "modelBaseUrl/SUNNY_MODEL_BASE_URL must be an HTTPS base URL ending in /."
}
val escapedModelBaseUrl = modelBaseUrl.replace("\\", "\\\\").replace("\"", "\\\"")
val modelLanguageSha256 = providers.gradleProperty("modelLanguageSha256")
    .orElse(providers.environmentVariable("SUNNY_MODEL_LANGUAGE_SHA256"))
    .getOrElse("e41e8bf3d8184980023bb2af2d0b565463f359a9b6c46a8e95b77da61af472ce")
    .trim()
    .lowercase()
val modelProjectorSha256 = providers.gradleProperty("modelProjectorSha256")
    .orElse(providers.environmentVariable("SUNNY_MODEL_PROJECTOR_SHA256"))
    .getOrElse("23474645acf3e10f7789cfb5dddacbf00a0d693b4f958b37ecc9b217071d7f46")
    .trim()
    .lowercase()
fun requireHexDigest(name: String, value: String) {
    check(value.matches(Regex("[0-9a-f]{16}|[0-9a-f]{64}"))) {
        "$name must be a 16-character debug prefix or a full 64-character SHA-256 digest."
    }
}
requireHexDigest("modelLanguageSha256", modelLanguageSha256)
requireHexDigest("modelProjectorSha256", modelProjectorSha256)
val escapedModelLanguageSha256 = modelLanguageSha256.replace("\"", "\\\"")
val escapedModelProjectorSha256 = modelProjectorSha256.replace("\"", "\\\"")
val privacyContact = providers.gradleProperty("privacyContact")
    .orElse(providers.environmentVariable("SUNNY_PRIVACY_CONTACT"))
    .getOrElse("")
    .trim()
check(
    privacyContact.isBlank() || privacyContact.startsWith("https://") ||
        privacyContact.matches(Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")),
) { "privacyContact/SUNNY_PRIVACY_CONTACT must be an email address or HTTPS URL." }
val escapedPrivacyContact = privacyContact.replace("\\", "\\\\").replace("\"", "\\\"")

// Device releases are not functional without the native bridge. Keeping this
// flag at the project level also lets the release gate validate the artifact.
val withLlama = project.hasProperty("withLlama") &&
    file("src/main/cpp/llama.cpp/CMakeLists.txt").exists()

// INTERIM beta server method. Environment values deliberately win over the
// checked-in development fallback, so CI can select device mode without editing
// repository files.
val inferenceMode = providers.environmentVariable("SUNNY_INFERENCE_MODE")
    .orElse(providers.gradleProperty("inferenceMode"))
    .getOrElse("auto")
    .trim()
    .lowercase()
check(inferenceMode in setOf("auto", "server", "device")) {
    "SUNNY_INFERENCE_MODE/inferenceMode must be auto, server, or device."
}
val configuredInferenceApiUrl = providers.environmentVariable("SUNNY_INFERENCE_API_URL")
    .orElse(providers.gradleProperty("inferenceApiUrl"))
    .getOrElse("")
    .trim()
val inferenceApiUrl = if (inferenceMode == "device") "" else configuredInferenceApiUrl
check(inferenceMode != "server" || inferenceApiUrl.isNotBlank()) {
    "Server mode requires SUNNY_INFERENCE_API_URL or inferenceApiUrl."
}
val escapedInferenceApiUrl = inferenceApiUrl.replace("\\", "\\\\").replace("\"", "\\\"")
val inferenceApiToken = providers.environmentVariable("SUNNY_INFERENCE_API_TOKEN")
    .getOrElse("")
    .trim()
val escapedInferenceApiToken = inferenceApiToken.replace("\\", "\\\\").replace("\"", "\\\"")

// INTERIM beta "improve Sunny" endpoint. It can be disabled independently of
// inference for production builds and local testing.
val contributionMode = providers.environmentVariable("SUNNY_CONTRIBUTION_MODE")
    .orElse(providers.gradleProperty("contributionMode"))
    .getOrElse("auto")
    .trim()
    .lowercase()
check(contributionMode in setOf("auto", "enabled", "disabled")) {
    "SUNNY_CONTRIBUTION_MODE/contributionMode must be auto, enabled, or disabled."
}
val configuredContributeUrl = providers.environmentVariable("SUNNY_CONTRIBUTE_URL")
    .orElse(providers.gradleProperty("contributeUrl"))
    .getOrElse("")
    .trim()
val contributeUrl = if (contributionMode == "disabled") "" else configuredContributeUrl
check(contributionMode != "enabled" || contributeUrl.isNotBlank()) {
    "Enabled contribution mode requires SUNNY_CONTRIBUTE_URL or contributeUrl."
}
val escapedContributeUrl = contributeUrl.replace("\\", "\\\\").replace("\"", "\\\"")
val contributeApiToken = providers.environmentVariable("SUNNY_CONTRIBUTE_API_TOKEN")
    .getOrElse("")
    .trim()
val escapedContributeApiToken = contributeApiToken.replace("\\", "\\\\").replace("\"", "\\\"")
val allowInsecureBetaEndpoints = providers.gradleProperty("allowInsecureBetaEndpoints")
    .map { it.toBoolean() }
    .getOrElse(false)

fun checkHttpEndpoint(name: String, value: String) {
    check(value.isBlank() || value.startsWith("https://") || value.startsWith("http://")) {
        "$name must be an HTTP(S) URL."
    }
    check(!value.startsWith("http://") || allowInsecureBetaEndpoints) {
        "$name uses cleartext HTTP. Use HTTPS or explicitly set " +
            "allowInsecureBetaEndpoints=true for a debug-only beta build."
    }
}
checkHttpEndpoint("inferenceApiUrl", inferenceApiUrl)
checkHttpEndpoint("contributeUrl", contributeUrl)

tasks.configureEach {
    if (name == "preReleaseBuild") {
        doFirst {
            check(dataRightsCleared) {
                "Release blocked: document commercial training-data/model rights, then pass " +
                    "-PdataRightsCleared=true. See RELEASE_READINESS.md."
            }
            check(clinicalValidationComplete) {
                "Release blocked: complete clinician-labelled phone-photo, skin-tone, safety, " +
                    "and real-device validation, then pass -PclinicalValidationComplete=true. " +
                    "See RELEASE_READINESS.md."
            }
            check(withLlama) {
                "Release blocked: build the on-device runtime with -PwithLlama."
            }
            check(modelBaseUrl.isNotBlank()) {
                "Release blocked: configure the rights-cleared HTTPS model CDN with " +
                    "-PmodelBaseUrl=https://.../."
            }
            check(modelLanguageSha256.length == 64 && modelProjectorSha256.length == 64) {
                "Release blocked: provide full model digests with -PmodelLanguageSha256 and " +
                    "-PmodelProjectorSha256 (or the matching SUNNY_MODEL_* environment values)."
            }
            check(privacyContact.isNotBlank()) {
                "Release blocked: configure a monitored privacy email or HTTPS URL with " +
                    "-PprivacyContact (or SUNNY_PRIVACY_CONTACT)."
            }
        }
    }
}

android {
    namespace = "com.sunny.skin"
    compileSdk = 35

    // Build the native llama.cpp/mtmd model bridge only when explicitly requested
    // (./gradlew assembleDebug -PwithLlama) AND llama.cpp has been vendored
    // (scripts/vendor_llama.sh). Default builds contain no inference fallback.
    defaultConfig {
        applicationId = "com.sunny.skin"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables { useSupportLibrary = true }
        buildConfigField("String", "SUNNY_MODEL_BASE_URL", "\"$escapedModelBaseUrl\"")
        buildConfigField("String", "SUNNY_MODEL_LANGUAGE_SHA256", "\"$escapedModelLanguageSha256\"")
        buildConfigField("String", "SUNNY_MODEL_PROJECTOR_SHA256", "\"$escapedModelProjectorSha256\"")
        buildConfigField("String", "SUNNY_PRIVACY_CONTACT", "\"$escapedPrivacyContact\"")
        buildConfigField("String", "SUNNY_INFERENCE_API_URL", "\"$escapedInferenceApiUrl\"")
        buildConfigField("String", "SUNNY_INFERENCE_API_TOKEN", "\"$escapedInferenceApiToken\"")
        buildConfigField("String", "SUNNY_CONTRIBUTE_URL", "\"$escapedContributeUrl\"")
        buildConfigField("String", "SUNNY_CONTRIBUTE_API_TOKEN", "\"$escapedContributeApiToken\"")
        buildConfigField("Boolean", "SUNNY_ALLOW_INSECURE_BETA_ENDPOINTS", allowInsecureBetaEndpoints.toString())
        buildConfigField("Boolean", "SUNNY_PUBLIC_RELEASE", "false")

        if (withLlama) {
            // A 6 GB model needs a 64-bit address space — arm64 only.
            ndk { abiFilters += "arm64-v8a" }
            externalNativeBuild {
                cmake {
                    arguments += listOf("-DANDROID_STL=c++_shared")
                    cppFlags += "-O3"
                }
            }
        }
    }

    if (withLlama) {
        externalNativeBuild {
            cmake {
                path = file("src/main/cpp/CMakeLists.txt")
                version = "3.22.1"
            }
        }
        ndkVersion = "28.2.13676358"
    }

    signingConfigs {
        if (keystoreProps.isNotEmpty()) {
            create("release") {
                storeFile = file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            // Public distribution is a distinct, fail-closed mode. These values
            // override every beta environment/property so a release artifact can
            // never contain or select the temporary server/contribution paths.
            isMinifyEnabled = true
            isShrinkResources = true
            buildConfigField("Boolean", "SUNNY_PUBLIC_RELEASE", "true")
            buildConfigField("String", "SUNNY_INFERENCE_API_URL", "\"\"")
            buildConfigField("String", "SUNNY_CONTRIBUTE_URL", "\"\"")
            buildConfigField("Boolean", "SUNNY_ALLOW_INSECURE_BETA_ENDPOINTS", "false")
            buildConfigField("String", "SUNNY_INFERENCE_API_TOKEN", "\"\"")
            buildConfigField("String", "SUNNY_CONTRIBUTE_API_TOKEN", "\"\"")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // Signed only when keystore.properties is present (see top of file).
            signingConfig = signingConfigs.findByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs += listOf(
            "-opt-in=androidx.compose.foundation.layout.ExperimentalLayoutApi",
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api",
        )
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
        // The GGUF model files are shipped as uncompressed assets so llama.cpp
        // can mmap them directly from the APK / OBB without a copy-out step.
        jniLibs.useLegacyPackaging = false
    }
    androidResources {
        // Do NOT compress the on-device model weights.
        noCompress += listOf("gguf", "litertlm", "bin")
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.activity:activity-compose:1.9.3")

    // Compose UI
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation("androidx.compose.ui:ui-tooling")

    // Navigation
    implementation("androidx.navigation:navigation-compose:2.8.5")

    // Room (on-device, private storage)
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    // CameraX (capture flow)
    implementation("androidx.camera:camera-core:1.4.1")
    implementation("androidx.camera:camera-camera2:1.4.1")
    implementation("androidx.camera:camera-lifecycle:1.4.1")
    implementation("androidx.camera:camera-view:1.4.1")

    // Biometric (Face ID / device credential lock)
    implementation("androidx.biometric:biometric:1.2.0-alpha05")

    // Coil for on-device image thumbnails
    implementation("io.coil-kt:coil-compose:2.7.0")

    // On-device encryption at rest: SQLCipher for the Room DB.
    implementation("net.zetetic:sqlcipher-android:4.6.1")
    implementation("androidx.sqlite:sqlite:2.4.0")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:core-ktx:1.6.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
}
