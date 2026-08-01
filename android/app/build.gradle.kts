import java.util.Properties
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermission

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

// Prefer an OS/CI secret store. The ignored properties file remains a local-only
// fallback, and should be mode 0600 when used.
val keystorePropsFile = rootProject.file("keystore.properties")
val keystoreProps = Properties().apply {
    if (keystorePropsFile.exists()) keystorePropsFile.inputStream().use { load(it) }
}
fun signingValue(environmentName: String, propertyName: String): String =
    providers.environmentVariable(environmentName).orNull?.trim().orEmpty()
        .ifBlank { keystoreProps.getProperty(propertyName, "").trim() }
val signingStoreFile = signingValue("SUNNY_SIGNING_STORE_FILE", "storeFile")
val signingStorePassword = signingValue("SUNNY_SIGNING_STORE_PASSWORD", "storePassword")
val signingKeyAlias = signingValue("SUNNY_SIGNING_KEY_ALIAS", "keyAlias")
val signingKeyPassword = signingValue("SUNNY_SIGNING_KEY_PASSWORD", "keyPassword")
val signingValues = listOf(signingStoreFile, signingStorePassword, signingKeyAlias, signingKeyPassword)
check(signingValues.all { it.isBlank() } || signingValues.all { it.isNotBlank() }) {
    "Signing configuration is incomplete. Provide all four SUNNY_SIGNING_* values."
}
val signingConfigured = signingValues.all { it.isNotBlank() }
fun requirePrivatePermissions(file: File) {
    if (!file.exists()) return
    val permissions = runCatching { Files.getPosixFilePermissions(file.toPath()) }.getOrNull() ?: return
    val unsafe = setOf(
        PosixFilePermission.GROUP_READ,
        PosixFilePermission.GROUP_WRITE,
        PosixFilePermission.GROUP_EXECUTE,
        PosixFilePermission.OTHERS_READ,
        PosixFilePermission.OTHERS_WRITE,
        PosixFilePermission.OTHERS_EXECUTE,
    )
    check(permissions.none { it in unsafe }) { "Signing file must be owner-only (chmod 600): $file" }
}
requirePrivatePermissions(keystorePropsFile)
if (signingConfigured) requirePrivatePermissions(project.file(signingStoreFile))

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
val privacyContact = providers.gradleProperty("privacyContact")
    .orElse(providers.environmentVariable("SUNNY_PRIVACY_CONTACT"))
    .getOrElse("")
    .trim()
check(
    privacyContact.isBlank() || privacyContact.startsWith("https://") ||
        privacyContact.matches(Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")),
) { "privacyContact/SUNNY_PRIVACY_CONTACT must be an email address or HTTPS URL." }
val escapedPrivacyContact = privacyContact.replace("\\", "\\\\").replace("\"", "\\\"")
val entitlementApiUrl = providers.gradleProperty("entitlementApiUrl")
    .orElse(providers.environmentVariable("SUNNY_ENTITLEMENT_API_URL"))
    .getOrElse("")
    .trim()
check(entitlementApiUrl.isBlank() || entitlementApiUrl.startsWith("https://")) {
    "entitlementApiUrl/SUNNY_ENTITLEMENT_API_URL must use HTTPS."
}
val escapedEntitlementApiUrl = entitlementApiUrl.replace("\\", "\\\\").replace("\"", "\\\"")
val betaEntitlementApiUrl = providers.gradleProperty("betaEntitlementApiUrl")
    .orElse(providers.environmentVariable("SUNNY_BETA_ENTITLEMENT_API_URL"))
    .getOrElse("")
    .trim()
check(betaEntitlementApiUrl.isBlank() || betaEntitlementApiUrl.startsWith("https://")) {
    "betaEntitlementApiUrl/SUNNY_BETA_ENTITLEMENT_API_URL must use HTTPS."
}
val escapedBetaEntitlementApiUrl = betaEntitlementApiUrl
    .replace("\\", "\\\\")
    .replace("\"", "\\\"")
val proProductId = providers.gradleProperty("proProductId").getOrElse("sunny_pro").trim()
val proIntroOfferTag = providers.gradleProperty("proIntroOfferTag").getOrElse("sunny-pro-intro").trim()
check(proProductId.matches(Regex("[a-z0-9._-]{1,128}"))) {
    "Play subscription product IDs contain invalid characters."
}
check(proIntroOfferTag.matches(Regex("[A-Za-z0-9._-]{1,128}"))) {
    "The Pro introductory offer tag contains invalid characters."
}

// The runtime is built from a pinned, patched llama.cpp source tree. Vendoring
// is explicit so a release cannot silently fall back to the retired Pro bridge.
val sunnyMoeRuntimeSourceReady =
    file("src/main/cpp/CMakeLists.txt").isFile &&
        file("src/main/cpp/sunny_moe.cpp").isFile &&
        file("src/main/cpp/llama.cpp/CMakeLists.txt").isFile

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
fun checkHttpsEndpoint(name: String, value: String) {
    check(value.isBlank() || value.startsWith("https://")) {
        "$name must be an HTTPS URL. Cleartext endpoints are never permitted."
    }
}
checkHttpsEndpoint("inferenceApiUrl", inferenceApiUrl)
checkHttpsEndpoint("contributeUrl", contributeUrl)

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
            check(sunnyMoeRuntimeSourceReady) {
                "Release blocked: run android/scripts/vendor_sunny_moe_runtime.sh first."
            }
            check(privacyContact.isNotBlank()) {
                "Release blocked: configure a monitored privacy email or HTTPS URL with " +
                    "-PprivacyContact (or SUNNY_PRIVACY_CONTACT)."
            }
            check(entitlementApiUrl.isNotBlank()) {
                "Release blocked: configure the server-side Play entitlement verifier with " +
                    "-PentitlementApiUrl=https://.../."
            }
        }
    }
}

android {
    namespace = "com.sunny.skin"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.sunny.skin"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables { useSupportLibrary = true }
        buildConfigField("String", "SUNNY_MODEL_BASE_URL", "\"$escapedModelBaseUrl\"")
        buildConfigField("String", "SUNNY_PRIVACY_CONTACT", "\"$escapedPrivacyContact\"")
        buildConfigField("String", "SUNNY_ENTITLEMENT_API_URL", "\"$escapedEntitlementApiUrl\"")
        buildConfigField("String", "SUNNY_PRO_PRODUCT_ID", "\"$proProductId\"")
        buildConfigField("String", "SUNNY_PRO_INTRO_OFFER_TAG", "\"$proIntroOfferTag\"")
        buildConfigField("String", "SUNNY_INFERENCE_API_URL", "\"\"")
        buildConfigField("String", "SUNNY_CONTRIBUTE_URL", "\"\"")
        buildConfigField("Boolean", "SUNNY_PUBLIC_RELEASE", "false")

        ndk { abiFilters += "arm64-v8a" }
        externalNativeBuild {
            cmake {
                arguments += "-DANDROID_STL=c++_shared"
                cppFlags += listOf("-O3", "-fexceptions", "-frtti")
            }
        }

    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }
    ndkVersion = "28.2.13676358"

    signingConfigs {
        if (signingConfigured) {
            create("release") {
                storeFile = file(signingStoreFile)
                storePassword = signingStorePassword
                keyAlias = signingKeyAlias
                keyPassword = signingKeyPassword
            }
        }
    }

    buildTypes {
        debug {
            // The private-beta broker returns short-lived authorization and signed
            // model URLs. No reusable download credential is compiled into the APK.
            buildConfigField(
                "String",
                "SUNNY_ENTITLEMENT_API_URL",
                "\"$escapedBetaEntitlementApiUrl\"",
            )
            buildConfigField("String", "SUNNY_INFERENCE_API_URL", "\"$escapedInferenceApiUrl\"")
            buildConfigField("String", "SUNNY_CONTRIBUTE_URL", "\"\"")
        }
        create("beta") {
            // Tester builds get release-grade process protections while retaining
            // explicitly configured HTTPS beta endpoints. Credentials are entered
            // at runtime and encrypted locally; they are never compiled into the APK.
            isDebuggable = false
            isMinifyEnabled = true
            isShrinkResources = true
            buildConfigField(
                "String",
                "SUNNY_ENTITLEMENT_API_URL",
                "\"$escapedBetaEntitlementApiUrl\"",
            )
            buildConfigField("String", "SUNNY_INFERENCE_API_URL", "\"$escapedInferenceApiUrl\"")
            buildConfigField("String", "SUNNY_CONTRIBUTE_URL", "\"$escapedContributeUrl\"")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.findByName("release")
            // Tester phones are arm64; excluding emulator/legacy ABIs avoids a
            // universal APK carrying unused emulator/legacy native ABIs.
            ndk { abiFilters += "arm64-v8a" }
        }
        release {
            // Public distribution is a distinct, fail-closed mode. These values
            // override every beta environment/property so a release artifact can
            // never contain or select the temporary server/contribution paths.
            isMinifyEnabled = true
            isShrinkResources = true
            buildConfigField("Boolean", "SUNNY_PUBLIC_RELEASE", "true")
            buildConfigField("String", "SUNNY_INFERENCE_API_URL", "\"\"")
            buildConfigField("String", "SUNNY_CONTRIBUTE_URL", "\"\"")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // Signed only when a complete environment or local fallback is present.
            signingConfig = signingConfigs.findByName("release")
            ndk { abiFilters += "arm64-v8a" }
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
        // Downloaded model files live in app-private storage, outside the APK.
        jniLibs.useLegacyPackaging = false
    }
    androidResources {
        // Do NOT compress the on-device model weights.
        noCompress += listOf("safetensors")
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

    // Coil for on-device image thumbnails
    implementation("io.coil-kt:coil-compose:2.7.0")

    // Store purchases are never trusted locally; tokens are verified by Sunny's backend.
    implementation("com.android.billingclient:billing:9.1.0")

    // On-device encryption at rest: SQLCipher for the Room DB.
    implementation("net.zetetic:sqlcipher-android:4.17.0")
    implementation("androidx.sqlite:sqlite:2.4.0")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20260522")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:core-ktx:1.6.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
}
