package com.sunny.skin.inference

import android.content.Context
import android.content.pm.ApplicationInfo
import com.sunny.skin.BuildConfig
import com.sunny.skin.data.SettingsStore
import com.sunny.skin.data.BetaCredentialStore
import com.sunny.skin.inference.download.ModelPack
import com.sunny.skin.inference.download.ModelPackCatalog
import com.sunny.skin.inference.tier.InferencePlacement
import com.sunny.skin.inference.tier.InferenceRoute
import com.sunny.skin.inference.tier.ModelAvailability
import com.sunny.skin.inference.tier.SunnyAccessPolicy
import com.sunny.skin.inference.tier.SunnyModelTier
import com.sunny.skin.subscription.SubscriptionEntitlements
import java.io.File

/**
 * Resolves the install-time Sunny Offline pack or the included cloud engine. Runtime
 * code deliberately has no mock fallback: a missing or broken model disables
 * scanning instead of producing plausible-looking fabricated health output.
 * Play delivers weights in an install-time asset pack. On first launch they are
 * verified and copied to the first directory below because llama.cpp requires
 * normal filesystem paths. Developer fallbacks are also supported:
 *
 *   1. filesDir/models/                              (internal, private; runtime target)
 *   2. getExternalFilesDir("models")                 (app external dir — adb push here for dev)
 *   3. /data/local/tmp/sunny/                        (last-resort dev push location)
 *
 * To use the local pack on a device/emulator (no download):
 *   ./scripts/push_weights_to_device.sh              (adb push -> external files dir)
 */
object ModelProvider {

    private val legacyLocalAssets = buildSet {
        addAll(listOf(
            "sunny-lite-mobilenetv3.onnx",
            "sunny-medium-convnexttiny.onnx",
            "e4b-derm-Q4_K_M.gguf",
            "mmproj-e4b-derm-f16.gguf",
            "mmproj-e4b-derm-Q8_0.gguf",
            "sunny-moe-text-Q4_K_M.gguf",
            "sunny-moe-mmproj-F16.gguf",
            "dense-00001.safetensors",
            "dense-00002.safetensors",
            "dense-00003.safetensors",
            "routers.safetensors",
            "correction-adapters.safetensors",
            "tokenizer.json",
            "tokenizer_config.json",
            "processor_config.json",
            "chat_template.jinja",
        ))
        (12..23).forEach { layer ->
            add("experts-layer-${layer.toString().padStart(3, '0')}.safetensors")
        }
    }

    /** Primary internal location for the verified runtime files. */
    fun modelsDir(context: Context) = File(context.filesDir, "models").apply { mkdirs() }

    /**
     * Candidate directories searched for the weight files, in priority order.
     * Release builds load ONLY from internal, app-private storage (the prepared
     * runtime target). The shared external dir and /data/local/tmp — both writable
     * without root and unverified — are dev conveniences, so they are scanned
     * only on debuggable builds where a model pack fed to native code couldn't be
     * planted by another app on a shipped install.
     */
    private fun candidateDirs(context: Context): List<File> {
        val dirs = mutableListOf(modelsDir(context))
        val debuggable = (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
        if (debuggable) {
            context.getExternalFilesDir("models")?.let { dirs.add(it) }
            dirs.add(File("/data/local/tmp/sunny"))
        }
        return dirs
    }

    /** Remove packs that this app version can no longer select or execute. */
    fun purgeLegacyLocalPacks(context: Context) {
        candidateDirs(context).forEach { directory ->
            legacyLocalAssets.forEach { fileName ->
                File(directory, fileName).delete()
                File(directory, "$fileName.part").delete()
            }
        }
    }

    private fun resolvePack(context: Context, pack: ModelPack): List<File>? {
        for (dir in candidateDirs(context)) {
            val files = pack.assets.map { File(dir, it.fileName) }
            if (files.zip(pack.assets).all { (file, asset) ->
                    file.isFile && file.length() == asset.sizeBytes
                }
            ) return files
        }
        return null
    }

    fun packPresent(context: Context, tier: SunnyModelTier): Boolean =
        ModelPackCatalog.publishedPack(tier)?.let { resolvePack(context, it) != null } == true

    fun weightsPresent(context: Context): Boolean =
        packPresent(context, SunnyModelTier.SUNNY_MOE)

    fun nativeRuntimeAvailable(): Boolean = SunnyMoeBridge.ensureLibrary()

    /** Human-readable location of the found weights, for the setup screen. */
    fun weightsLocation(context: Context): String? =
        resolvePack(context, ModelPackCatalog.sunnyMoe)?.firstOrNull()?.parentFile?.absolutePath

    /** True once [describer] has been created against a real local/remote engine. */
    @Volatile var usingRealModel: Boolean = false
        private set

    /** True when a configured remote inference API (server method) is set. */
    val serverApiUrl: String get() = BuildConfig.SUNNY_INFERENCE_API_URL

    /**
     * Whether the next scan should use the included remote server. Public builds
     * accept a short-lived server authorization; beta builds may use their
     * separately provisioned engineering credential. Pro gates the bundled
     * on-device model, not server analysis.
     */
    fun useServer(context: Context): Boolean = remoteAuthorization(context) != null

    private fun remoteAuthorization(context: Context): Pair<String, String>? {
        val app = context.applicationContext
        val settings = SettingsStore(app)
        if (!settings.useServerInference || settings.cloudAnalysisConsentAt <= 0L) return null
        return configuredRemoteAuthorization(app)
    }

    /** Server capability independent of the user's current analysis-source choice. */
    fun serverAvailable(context: Context): Boolean =
        configuredRemoteAuthorization(context.applicationContext) != null

    private fun configuredRemoteAuthorization(context: Context): Pair<String, String>? {
        val now = System.currentTimeMillis()
        val paid = SubscriptionEntitlements.cloudInference.value
        if (paid?.isValid(now) == true && !paid.quota.exhausted) {
            return paid.baseUrl to paid.bearerToken
        }
        if (!BuildConfig.SUNNY_PUBLIC_RELEASE && serverApiUrl.isNotBlank()) {
            val token = BetaCredentialStore(context).inferenceToken
            if (token.isNotBlank()) return serverApiUrl to token
        }
        return null
    }

    /**
     * Analysis is available when EITHER the server path is selected and configured
     * OR the on-device weights + native runtime are both present. This does not
     * load the 605 MB pack into memory.
     */
    fun realModelAvailable(context: Context): Boolean =
        useServer(context) ||
            localRoute(context) != null

    /** Local readiness only; cloud authorization must not imply local readiness. */
    fun localModelAvailable(context: Context): Boolean = localRoute(context) != null

    /** False for hardware whose native accelerator path failed physical-device validation. */
    fun localDeviceSupported(): Boolean =
        DeviceInferenceCapabilities.supportsReliableOnDeviceInference()

    private fun localRoute(context: Context): InferenceRoute? {
        if (!localDeviceSupported()) return null
        val availability = ModelAvailability(
            sunnyMoeInstalled = packPresent(context, SunnyModelTier.SUNNY_MOE) &&
                SunnyMoeBridge.ensureLibrary(),
            proCloudAvailable = false,
            cloudProcessingConsented = false,
        )
        return SunnyAccessPolicy.preferredRoute(
            entitlement = SubscriptionEntitlements.accessEntitlement(),
            availability = availability,
            nowMillis = System.currentTimeMillis(),
        )?.takeIf { it.placement == InferencePlacement.ON_DEVICE }
    }

    /** Single shared describer per process (keeps the model warm across screens). */
    @Volatile private var describer: SunnyDescriber? = null
    @Volatile private var describerRouteKey: String? = null
    @Volatile private var describerValidUntilMillis: Long = Long.MIN_VALUE

    fun describer(context: Context): SunnyDescriber? {
        val app = context.applicationContext
        val now = System.currentTimeMillis()
        val remote = remoteAuthorization(app)
        val route = if (remote == null) localRoute(app) else null
        val routeKey = remote?.let { "cloud:${it.first}" } ?: route?.let { "local:${it.tier.name}" }
            ?: run {
                reset()
                return null
            }
        val entitlement = SubscriptionEntitlements.current.value
        val cloud = SubscriptionEntitlements.cloudInference.value
        val validUntil = when {
            remote != null && cloud?.baseUrl == remote.first && cloud.isValid(now) ->
                minOf(entitlement.verifiedUntilMillis, cloud.expiresAtMillis)
            remote != null -> Long.MAX_VALUE // private beta credential
            route?.tier == SunnyModelTier.SUNNY_MOE || BuildConfig.DEBUG -> Long.MAX_VALUE
            else -> entitlement.verifiedUntilMillis
        }
        describer?.let {
            if (describerRouteKey == routeKey && validUntil > now) return it
        }
        return synchronized(this) {
            describer?.let {
                if (describerRouteKey == routeKey && describerValidUntilMillis > now) {
                    return@synchronized it
                }
                it.close()
                describer = null
            }
            remote?.let { (url, token) ->
                return@synchronized SunnyDescriber(
                    RemoteSunnyModel(url, token) {
                        SubscriptionEntitlements.recordCloudAnalysis()
                    },
                ).also {
                    describer = it
                    describerRouteKey = routeKey
                    describerValidUntilMillis = validUntil
                    usingRealModel = true
                }
            }
            val model = when (route?.tier) {
                SunnyModelTier.SUNNY_MOE -> {
                    val pack = resolvePack(app, ModelPackCatalog.sunnyMoe)
                        ?: return@synchronized null
                    val directory = pack.firstOrNull()?.parentFile ?: return@synchronized null
                    SunnyMoeModel(
                        packDirectory = directory.absolutePath,
                        nativeLibraryDirectory = app.applicationInfo.nativeLibraryDir,
                    )
                }
                SunnyModelTier.PRO_CLOUD -> return@synchronized null
                null -> return@synchronized null
            }
            SunnyDescriber(model).also {
                describer = it
                describerRouteKey = routeKey
                describerValidUntilMillis = validUntil
                usingRealModel = true
            }
        }
    }

    /**
     * Drop the cached describer so the next request resolves the installed model.
     * The previous native session is closed to avoid retaining several GB of RAM.
     */
    fun reset() {
        synchronized(this) {
            describer?.close()
            describer = null
            describerRouteKey = null
            describerValidUntilMillis = Long.MIN_VALUE
            usingRealModel = false
        }
    }
}
