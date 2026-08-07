package com.sunny.skin.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TextButton
import com.sunny.skin.ui.i18n.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sunny.skin.inference.download.ModelDownloadManager
import com.sunny.skin.inference.download.BundledModelInstaller
import com.sunny.skin.inference.download.ModelPackCatalog
import com.sunny.skin.inference.download.ModelSource
import com.sunny.skin.inference.download.ModelStatus
import com.sunny.skin.inference.tier.SunnyModelTier
import com.sunny.skin.inference.tier.SunnyPlan
import com.sunny.skin.ui.SunnyViewModel
import com.sunny.skin.ui.components.DisclaimerCard
import com.sunny.skin.ui.components.ScreenScaffold
import com.sunny.skin.ui.components.SunnyCard
import com.sunny.skin.ui.components.SunnyChip
import com.sunny.skin.ui.theme.SunnyColors
import com.sunny.skin.ui.theme.SunnyMotion
import com.sunny.skin.ui.theme.rememberSunnyMotionEnabled

/**
 * PAD-trained Sunny Offline setup. Play installs the model with the app; Sunny
 * verifies and prepares it privately before first use.
 * Sunny Pro is represented only by the consented server route.
 */
@Composable
fun ModelSetupScreen(vm: SunnyViewModel, onBack: () -> Unit) {
    val status by ModelDownloadManager.status.collectAsStateWithLifecycle()
    val context = androidx.compose.ui.platform.LocalContext.current
    val serverSelected by vm.useServerInference.collectAsStateWithLifecycle()
    var showCloudConsent by remember { mutableStateOf(false) }
    var allowMobileData by androidx.compose.runtime.saveable.rememberSaveable {
        androidx.compose.runtime.mutableStateOf(true)
    }
    var showOfflineDetails by androidx.compose.runtime.saveable.rememberSaveable {
        androidx.compose.runtime.mutableStateOf(!vm.settings.useServerInference)
    }
    val targetTier = SunnyModelTier.SUNNY_MOE
    val targetPack = ModelPackCatalog.sunnyMoe
    val bundledModelIncluded = remember(context) {
        BundledModelInstaller.contains(context, targetPack)
    }
    val storePlans by com.sunny.skin.SunnyApp.instance.billing.plans.collectAsStateWithLifecycle()
    val billingState by com.sunny.skin.SunnyApp.instance.billing.state.collectAsStateWithLifecycle()
    val verifiedEntitlement by com.sunny.skin.subscription.SubscriptionEntitlements.current
        .collectAsStateWithLifecycle()
    val cloudAuthorization by com.sunny.skin.subscription.SubscriptionEntitlements.cloudInference
        .collectAsStateWithLifecycle()
    val modelDownloadAuthorization by com.sunny.skin.subscription.SubscriptionEntitlements.modelDownloads
        .collectAsStateWithLifecycle()
    val now = System.currentTimeMillis()
    val accessEntitlement = if (
        com.sunny.skin.BuildConfig.DEBUG &&
        com.sunny.skin.BuildConfig.SUNNY_ENTITLEMENT_API_URL.isBlank()
    ) {
        com.sunny.skin.subscription.SubscriptionEntitlements.accessEntitlement()
    } else {
        verifiedEntitlement
    }
    val hasProAccess = accessEntitlement.effectivePlan(now) == SunnyPlan.PRO
    val cloudReady = cloudAuthorization?.isValid(now) == true ||
        com.sunny.skin.inference.ModelProvider.serverAvailable(context)
    val localSupported = com.sunny.skin.inference.ModelProvider.localDeviceSupported()
    val downloadSourceAvailable = bundledModelIncluded ||
        modelDownloadAuthorization?.isValid(now) == true ||
        ModelSource.privateBetaOriginConfigured
    // Selection is a user preference and must remain visible even while a
    // short-lived authorization is refreshing. Conflating it with readiness was
    // what produced the dead "Not configured" screen.
    val localOperation = status is ModelStatus.Downloading || status == ModelStatus.Verifying
    // Pick up weights that were adb-pushed onto the device while the app was open.
    androidx.compose.runtime.LaunchedEffect(Unit) { ModelDownloadManager.refresh() }
    androidx.compose.runtime.LaunchedEffect(serverSelected, localOperation) {
        if (!serverSelected || localOperation) showOfflineDetails = true
    }

    ScreenScaffold(title = "Analysis setup", onBack = onBack) { inner ->
        Column(
            Modifier.fillMaxSize().padding(inner).verticalScroll(rememberScrollState()).padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(8.dp))
            StatusHeader(status, serverSelected, cloudReady)
            Spacer(Modifier.height(16.dp))
            ModelPlanOverview(
                serverSelected,
                cloudReady,
                hasProAccess,
                bundledModelIncluded,
                cloudAuthorization?.quota,
            )
            if (com.sunny.skin.AppMode.serverMode) {
                Spacer(Modifier.height(12.dp))
                OutlinedButton(
                    onClick = {
                        if (serverSelected) {
                            vm.setUseServerInference(false)
                            showOfflineDetails = true
                        } else {
                            showCloudConsent = true
                        }
                    },
                    enabled = serverSelected || cloudReady,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Text(
                        when {
                            serverSelected -> "Switch to on-device analysis"
                            cloudReady -> "Use cloud analysis"
                            else -> "Cloud analysis unavailable"
                        },
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
            if (!hasProAccess &&
                (storePlans.isNotEmpty() || billingState is com.sunny.skin.subscription.BillingState.Error)
            ) {
                Spacer(Modifier.height(16.dp))
                StorePlanActions(storePlans, billingState)
            }
            if (hasProAccess && serverSelected && !showOfflineDetails && !localOperation) {
                Spacer(Modifier.height(16.dp))
                OutlinedButton(
                    onClick = { showOfflineDetails = true },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Text(
                        if (status is ModelStatus.Ready) "Manage installed offline analysis"
                        else "Set up offline analysis",
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }

            if (hasProAccess && (showOfflineDetails || !serverSelected || localOperation)) {
                Spacer(Modifier.height(16.dp))
                ModelPipeline(status, serverSelected)
                Spacer(Modifier.height(12.dp))
                Text(
                    (if (bundledModelIncluded) {
                        "Offline analysis is included in the app installation. Sunny verifies and " +
                            "prepares ${formatSize(targetPack.totalBytes)} privately on this phone. "
                    } else {
                        "Offline analysis uses ${formatSize(targetPack.totalBytes)} and works " +
                            "without internet after installation. "
                    }) + "Android can preserve it only " +
                        "when you choose “Keep app data” during uninstall.",
                    style = MaterialTheme.typography.bodySmall,
                    color = SunnyColors.TextSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 8.dp),
                )
                Spacer(Modifier.height(16.dp))

                val motionEnabled = rememberSunnyMotionEnabled()
                AnimatedContent(
                targetState = status,
                contentKey = { it.toUiPhase() },
                transitionSpec = {
                    fadeIn(tween(SunnyMotion.StateMillis, easing = SunnyMotion.EaseOut)) togetherWith
                        fadeOut(tween(SunnyMotion.StateMillis, easing = SunnyMotion.EaseOut))
                },
                contentAlignment = Alignment.TopCenter,
                modifier = Modifier.fillMaxWidth(),
                label = "Model setup state",
            ) { visibleStatus ->
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    when (val s = visibleStatus) {
                ModelStatus.NotConfigured, ModelStatus.Idle, is ModelStatus.Failed -> {
                    (s as? ModelStatus.Failed)?.let {
                        Text(it.message, color = SunnyColors.Danger,
                            style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
                        Spacer(Modifier.height(12.dp))
                    }
                    ConnectionNote(targetTier, targetPack.totalBytes, bundledModelIncluded)
                    if (!bundledModelIncluded) {
                        Spacer(Modifier.height(12.dp))
                        DownloadNetworkChoice(
                            allowMobileData = allowMobileData,
                            onAllowMobileDataChange = { allowMobileData = it },
                        )
                    }
                    if (!downloadSourceAvailable) {
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "The secure Pro download service is currently unavailable. " +
                                "Your cloud selection is unchanged.",
                            style = MaterialTheme.typography.bodySmall,
                            color = SunnyColors.Review,
                            textAlign = TextAlign.Center,
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                    Button(
                        onClick = {
                            ModelDownloadManager.start(
                                tier = targetTier,
                                allowMetered = allowMobileData,
                            )
                        },
                        enabled = downloadSourceAvailable,
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = SunnyColors.Action),
                    ) {
                        Icon(
                            if (bundledModelIncluded) Icons.Filled.Verified else Icons.Filled.CloudDownload,
                            null,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(Modifier.size(8.dp))
                        Text(
                            when {
                                !downloadSourceAvailable -> "Download service unavailable"
                                s is ModelStatus.Failed && bundledModelIncluded -> "Retry preparation"
                                s is ModelStatus.Failed -> "Retry download"
                                bundledModelIncluded -> "Prepare included model"
                                else -> "Download offline model"
                            },
                            fontWeight = FontWeight.SemiBold)
                    }
                }
                is ModelStatus.Downloading -> {
                    val animatedProgress by animateFloatAsState(
                        targetValue = s.fraction.coerceIn(0f, 1f),
                        animationSpec = if (motionEnabled) {
                            tween(SunnyMotion.StateMillis, easing = LinearEasing)
                        } else {
                            snap()
                        },
                        label = "Model download progress",
                    )
                    LinearProgressIndicator(
                        progress = { animatedProgress },
                        modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(50)),
                        color = SunnyColors.Orange, trackColor = SunnyColors.SurfaceMuted,
                    )
                    Spacer(Modifier.height(10.dp))
                    Text("${gib(s.done)} / ${gib(s.total)}  ·  ${(s.fraction * 100).toInt()}%",
                        style = MaterialTheme.typography.bodyMedium, color = SunnyColors.TextSecondary)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        downloadEstimateLabel(s.bytesPerSecond, s.etaSeconds),
                        style = MaterialTheme.typography.bodySmall,
                        color = SunnyColors.TextTertiary,
                    )
                    Spacer(Modifier.height(16.dp))
                    OutlinedButton(onClick = { ModelDownloadManager.cancel() },
                        modifier = Modifier.fillMaxWidth()) { Text("Cancel") }
                }
                ModelStatus.Verifying -> InfoRow(
                    text = "Preparing and verifying the included offline model…",
                )
                ModelStatus.Ready -> {
                    Text(
                        if (localSupported) {
                            "Offline analysis is installed and ready."
                        } else {
                            com.sunny.skin.inference.DeviceInferenceCapabilities.unavailableReason()
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = SunnyColors.TextSecondary,
                        textAlign = TextAlign.Center,
                    )
                }
                    }
                }
            }
            }

            Spacer(Modifier.height(24.dp))
            DisclaimerCard(
                title = "About the AI",
                body = com.sunny.skin.AppMode.aiDescription(serverSelected),
            )
        }
    }

    if (showCloudConsent) {
        AlertDialog(
            onDismissRequest = { showCloudConsent = false },
            containerColor = SunnyColors.Surface,
            title = { Text("Use cloud analysis?") },
            text = {
                Text(
                    "Each photo you analyse will be sent over HTTPS to Sunny AI Cloud for a " +
                        "visual description. Saved photos and notes remain encrypted on this " +
                        "device. You can switch back to on-device analysis anytime.",
                    color = SunnyColors.TextSecondary,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.setUseServerInference(true)
                    showOfflineDetails = false
                    showCloudConsent = false
                }) {
                    Text("Use cloud analysis", color = SunnyColors.OrangeText)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCloudConsent = false }) {
                    Text("Keep on device", color = SunnyColors.TextSecondary)
                }
            },
        )
    }
}

@Composable
private fun StorePlanActions(
    plans: List<com.sunny.skin.subscription.StorePlan>,
    state: com.sunny.skin.subscription.BillingState,
) {
    val activity = androidx.compose.ui.platform.LocalContext.current as? android.app.Activity
    val context = androidx.compose.ui.platform.LocalContext.current
    SunnyCard {
        Column(Modifier.padding(16.dp)) {
            Text(
                "Membership",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Free includes 5 cloud analyses per month (maximum 2 per day). Pro includes " +
                    "100 per month plus unlimited offline analysis, advanced " +
                    "comparisons and reports.",
                style = MaterialTheme.typography.bodySmall,
                color = SunnyColors.TextSecondary,
            )
            plans.forEach { offer ->
                Spacer(Modifier.height(12.dp))
                OutlinedButton(
                    onClick = {
                        activity?.let {
                            com.sunny.skin.SunnyApp.instance.billing.launchPurchase(it, offer)
                        }
                    },
                    enabled = activity != null,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        buildString {
                            append(if (offer.billingPeriod == "P1Y") "Pro annual" else "Pro monthly")
                            if (offer.recurringPrice.isNotBlank()) {
                                append(" · ${offer.recurringPrice}")
                                append(if (offer.billingPeriod == "P1Y") "/year" else "/month")
                            }
                            if (offer.includesIntroOffer && offer.introPrice != null) {
                                append(" · ${offer.introPrice} first month")
                            }
                        },
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "Subscriptions renew automatically until cancelled. A first-month offer " +
                    "converts to the displayed monthly price. Google Play shows the final " +
                    "localized price and billing terms before purchase.",
                style = MaterialTheme.typography.bodySmall,
                color = SunnyColors.TextSecondary,
            )
            Spacer(Modifier.height(4.dp))
            androidx.compose.material3.TextButton(
                onClick = {
                    val uri = android.net.Uri.parse(
                        "https://play.google.com/store/account/subscriptions" +
                            "?sku=${com.sunny.skin.BuildConfig.SUNNY_PRO_PRODUCT_ID}" +
                            "&package=${com.sunny.skin.BuildConfig.APPLICATION_ID}",
                    )
                    context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, uri))
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Manage or cancel subscription")
            }
            (state as? com.sunny.skin.subscription.BillingState.Error)?.let { error ->
                Spacer(Modifier.height(8.dp))
                Text(error.message, style = MaterialTheme.typography.bodySmall, color = SunnyColors.Danger)
            }
        }
    }
}

@Composable
private fun ModelPlanOverview(
    serverSelected: Boolean,
    cloudReady: Boolean,
    hasProAccess: Boolean,
    bundledModelIncluded: Boolean,
    quota: com.sunny.skin.subscription.CloudQuota?,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val localInstalled = com.sunny.skin.inference.ModelProvider.packPresent(
        context,
        SunnyModelTier.SUNNY_MOE,
    )
    SunnyCard {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Text(
                "Analysis options",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Use the cloud, or choose private offline analysis with Pro.",
                style = MaterialTheme.typography.bodySmall,
                color = SunnyColors.TextSecondary,
            )
            Spacer(Modifier.height(12.dp))
            ModelPlanRow(
                tier = SunnyModelTier.PRO_CLOUD,
                badge = "INCLUDED",
                state = when {
                    quota != null ->
                        "${quota.monthlyRemaining}/${quota.monthlyLimit} this month · " +
                            "${quota.dailyRemaining}/${quota.dailyLimit} today"
                    serverSelected && cloudReady -> "Selected · Cloud ready"
                    serverSelected -> "Selected · service connection unavailable"
                    else -> "Available · explicit opt-in required"
                },
                stateColor = when {
                    quota?.exhausted == true -> SunnyColors.Review
                    serverSelected && cloudReady -> SunnyColors.Success
                    serverSelected -> SunnyColors.Review
                    else -> SunnyColors.TextSecondary
                },
            )
            HorizontalDivider(color = SunnyColors.Divider)
            ModelPlanRow(
                tier = SunnyModelTier.SUNNY_MOE,
                badge = "PRO",
                state = when {
                    localInstalled && hasProAccess -> "Installed"
                    localInstalled -> "Installed · Pro required to use"
                    bundledModelIncluded && hasProAccess -> "Included with app · ready to prepare"
                    bundledModelIncluded -> "Included with app · Pro required to use"
                    hasProAccess -> "Ready to download · 605 MB"
                    else -> "Pro required"
                },
                stateColor = if (localInstalled) SunnyColors.OrangeText else SunnyColors.TextSecondary,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                if (bundledModelIncluded) {
                    "The offline model arrives with the app installation; no second network download is required."
                } else {
                    "Cloud analysis does not download a model. Offline analysis stores 605 MB privately on this phone."
                },
                style = MaterialTheme.typography.bodySmall,
                color = SunnyColors.TextTertiary,
            )
        }
    }
}

@Composable
private fun ModelPlanRow(
    tier: SunnyModelTier,
    badge: String,
    state: String,
    stateColor: androidx.compose.ui.graphics.Color,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (tier == SunnyModelTier.PRO_CLOUD) "Cloud analysis" else "Offline analysis",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    badge,
                    style = MaterialTheme.typography.labelSmall,
                    color = SunnyColors.OrangeText,
                    modifier = Modifier
                        .background(SunnyColors.OrangeSoft, RoundedCornerShape(50))
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                )
            }
            Spacer(Modifier.height(3.dp))
            Text(
                if (tier == SunnyModelTier.PRO_CLOUD) {
                    "Fast analysis using Sunny's secure server."
                } else {
                    "Private analysis that works without internet after preparation."
                },
                style = MaterialTheme.typography.bodySmall,
                color = SunnyColors.TextSecondary,
            )
            Spacer(Modifier.height(3.dp))
            Text(state, style = MaterialTheme.typography.labelMedium, color = stateColor)
        }
        Spacer(Modifier.width(12.dp))
        Text(
            tier.localSizeLabel,
            style = MaterialTheme.typography.labelMedium,
            color = SunnyColors.TextTertiary,
            textAlign = TextAlign.End,
        )
    }
}

@Composable
private fun StatusHeader(status: ModelStatus, serverSelected: Boolean, cloudReady: Boolean) {
    val motionEnabled = rememberSunnyMotionEnabled()
    var readyShown by remember { mutableStateOf(false) }
    LaunchedEffect(status is ModelStatus.Ready) { readyShown = status is ModelStatus.Ready }
    val readyEntrance by animateFloatAsState(
        targetValue = if (readyShown) 1f else 0f,
        animationSpec = tween(SunnyMotion.StateMillis, easing = SunnyMotion.EaseOut),
        label = "Model ready header",
    )
    val localOperation = status is ModelStatus.Downloading || status == ModelStatus.Verifying
    val (icon, tint) = when {
        serverSelected && !localOperation && cloudReady ->
            Icons.Filled.CloudDownload to SunnyColors.Success
        serverSelected && !localOperation ->
            Icons.Filled.ErrorOutline to SunnyColors.Review
        else -> when (status) {
        ModelStatus.Ready -> Icons.Filled.CheckCircle to SunnyColors.Success
        is ModelStatus.Failed, ModelStatus.NotConfigured -> Icons.Filled.ErrorOutline to SunnyColors.Review
        else -> Icons.Filled.CloudDownload to SunnyColors.Orange
        }
    }
    Icon(
        icon,
        null,
        tint = tint,
        modifier = Modifier.size(56.dp).graphicsLayer {
            if (status is ModelStatus.Ready) {
                alpha = readyEntrance
                if (motionEnabled) {
                    scaleX = 0.92f + (0.08f * readyEntrance)
                    scaleY = scaleX
                }
            }
        },
    )
    Spacer(Modifier.height(8.dp))
    val label = if (serverSelected && !localOperation) {
        if (cloudReady) "Cloud analysis ready" else "Cloud analysis unavailable"
    } else when (status) {
        ModelStatus.Ready -> "Offline analysis installed"
        is ModelStatus.Downloading -> "Downloading model"
        ModelStatus.Verifying -> "Preparing offline model"
        ModelStatus.NotConfigured -> "Offline model unavailable"
        is ModelStatus.Failed -> "Setup paused"
        ModelStatus.Idle -> "Offline analysis available"
    }
    Text(label, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
}

@Composable
private fun ConnectionNote(tier: SunnyModelTier, bytes: Long?, bundled: Boolean) {
    InfoRow(
        text = if (bundled && bytes != null) {
            "Included with the app · ${formatSize(bytes)} · no network download."
        } else if (bytes == null) {
            "${tier.displayName} is awaiting publication."
        } else {
            "${formatSize(bytes)}. Downloads over Wi-Fi or mobile data and resumes if interrupted."
        },
    )
}

@Composable
private fun DownloadNetworkChoice(
    allowMobileData: Boolean,
    onAllowMobileDataChange: (Boolean) -> Unit,
) {
    SunnyCard {
        Column(Modifier.padding(14.dp)) {
            Text(
                "Download using",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth()) {
                SunnyChip(
                    label = "Wi-Fi + mobile",
                    selected = allowMobileData,
                    onClick = { onAllowMobileDataChange(true) },
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                SunnyChip(
                    label = "Wi-Fi only",
                    selected = !allowMobileData,
                    onClick = { onAllowMobileDataChange(false) },
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                if (allowMobileData) {
                    "Mobile-data charges may apply. The download resumes if interrupted."
                } else {
                    "The download starts only on an unmetered Wi-Fi or ethernet connection."
                },
                style = MaterialTheme.typography.bodySmall,
                color = SunnyColors.TextSecondary,
            )
        }
    }
}

@Composable
private fun InfoRow(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Filled.CloudDownload, null, tint = SunnyColors.OrangeText, modifier = Modifier.size(18.dp))
        Spacer(Modifier.size(8.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = SunnyColors.TextSecondary)
    }
}

private enum class ModelUiPhase { Idle, Downloading, Verifying, Ready, Failed }

private fun ModelStatus.toUiPhase(): ModelUiPhase = when (this) {
    ModelStatus.NotConfigured -> ModelUiPhase.Idle
    ModelStatus.Idle -> ModelUiPhase.Idle
    is ModelStatus.Downloading -> ModelUiPhase.Downloading
    ModelStatus.Verifying -> ModelUiPhase.Verifying
    ModelStatus.Ready -> ModelUiPhase.Ready
    is ModelStatus.Failed -> ModelUiPhase.Failed
}

@Composable
private fun ModelPipeline(status: ModelStatus, serverSelected: Boolean) {
    val motionEnabled = rememberSunnyMotionEnabled()
    var readyShown by remember { mutableStateOf(false) }
    LaunchedEffect(status is ModelStatus.Ready) { readyShown = status is ModelStatus.Ready }
    val readyEntrance by animateFloatAsState(
        targetValue = if (readyShown) 1f else 0f,
        animationSpec = tween(SunnyMotion.StateMillis, easing = SunnyMotion.EaseOut),
        label = "Model ready pipeline",
    )
    SunnyCard {
        Column(Modifier.padding(16.dp)) {
            Text(
                "Offline setup",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            if (serverSelected) {
                Spacer(Modifier.height(4.dp))
                Text(
                    "Cloud is selected. Preparing offline analysis is optional for Pro users.",
                    style = MaterialTheme.typography.bodySmall,
                    color = SunnyColors.TextSecondary,
                )
            }
            Spacer(Modifier.height(14.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
            ) {
                PipelineStep(
                    label = "Prepare",
                    icon = Icons.Filled.CloudDownload,
                    state = status.downloadStepState(serverSelected),
                    modifier = Modifier.weight(1f),
                )
                PipelineConnector(active = status is ModelStatus.Verifying || status is ModelStatus.Ready)
                PipelineStep(
                    label = "Verify",
                    icon = Icons.Filled.Verified,
                    state = status.verifyStepState(),
                    modifier = Modifier.weight(1f),
                )
                PipelineConnector(active = status is ModelStatus.Ready)
                PipelineStep(
                    label = "Ready",
                    icon = Icons.Filled.CheckCircle,
                    state = if (status is ModelStatus.Ready) PipelineStepState.Complete else PipelineStepState.Waiting,
                    readyEntrance = readyEntrance,
                    spatialEntrance = motionEnabled,
                    modifier = Modifier.weight(1f),
                )
            }
            if (status is ModelStatus.Failed || status is ModelStatus.NotConfigured) {
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .background(SunnyColors.SurfaceMuted, RoundedCornerShape(12.dp))
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.Refresh, null, tint = SunnyColors.TextTertiary,
                        modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (status is ModelStatus.NotConfigured) "Offline model setup is unavailable"
                        else "Setup paused · retry when ready",
                        style = MaterialTheme.typography.bodySmall,
                        color = SunnyColors.TextSecondary,
                    )
                }
            }
        }
    }
}

private enum class PipelineStepState { Waiting, Current, Complete }

private fun ModelStatus.downloadStepState(serverSelected: Boolean): PipelineStepState = when (this) {
    is ModelStatus.Downloading -> PipelineStepState.Current
    ModelStatus.Idle -> if (serverSelected) PipelineStepState.Waiting else PipelineStepState.Current
    ModelStatus.Verifying, ModelStatus.Ready -> PipelineStepState.Complete
    else -> PipelineStepState.Waiting
}

private fun ModelStatus.verifyStepState(): PipelineStepState = when (this) {
    ModelStatus.Verifying -> PipelineStepState.Current
    ModelStatus.Ready -> PipelineStepState.Complete
    else -> PipelineStepState.Waiting
}

@Composable
private fun PipelineStep(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    state: PipelineStepState,
    readyEntrance: Float = 1f,
    spatialEntrance: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val background = when (state) {
        PipelineStepState.Waiting -> SunnyColors.SurfaceMuted
        PipelineStepState.Current -> SunnyColors.OrangeSoft
        PipelineStepState.Complete -> if (label == "Ready") SunnyColors.Success.copy(alpha = 0.14f)
        else SunnyColors.OrangeSoft
    }
    val tint = when (state) {
        PipelineStepState.Waiting -> SunnyColors.TextTertiary
        PipelineStepState.Current -> SunnyColors.OrangeText
        PipelineStepState.Complete -> if (label == "Ready") SunnyColors.Success else SunnyColors.OrangeText
    }
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(38.dp).background(background, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(20.dp).graphicsLayer {
                    if (label == "Ready" && state == PipelineStepState.Complete) {
                        alpha = readyEntrance
                        if (spatialEntrance) {
                            scaleX = 0.92f + (0.08f * readyEntrance)
                            scaleY = scaleX
                        }
                    }
                },
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = if (state == PipelineStepState.Current) SunnyColors.TextPrimary else SunnyColors.TextSecondary,
            fontWeight = if (state == PipelineStepState.Current) FontWeight.SemiBold else FontWeight.Normal,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun PipelineConnector(active: Boolean) {
    Box(
        Modifier.padding(top = 18.dp).width(18.dp).height(2.dp)
            .background(if (active) SunnyColors.OrangeLight else SunnyColors.Divider),
    )
}

private fun gib(bytes: Long): String = formatSize(bytes)

private fun downloadEstimateLabel(bytesPerSecond: Long, etaSeconds: Long?): String {
    if (bytesPerSecond <= 0L || etaSeconds == null) return "Calculating time remaining…"
    val speed = when {
        bytesPerSecond >= 1_000_000L -> "%.1f MB/s".format(bytesPerSecond / 1_000_000.0)
        bytesPerSecond >= 1_000L -> "%.0f KB/s".format(bytesPerSecond / 1_000.0)
        else -> "$bytesPerSecond B/s"
    }
    val remaining = when {
        etaSeconds < 60L -> "about ${etaSeconds.coerceAtLeast(1L)} sec left"
        etaSeconds < 3_600L -> "about ${(etaSeconds + 59L) / 60L} min left"
        else -> {
            val hours = etaSeconds / 3_600L
            val minutes = (etaSeconds % 3_600L + 59L) / 60L
            if (minutes == 0L) "about $hours hr left" else "about $hours hr $minutes min left"
        }
    }
    return "$speed · $remaining"
}

private fun formatSize(bytes: Long): String = if (bytes < 100_000_000L) {
    "%.1f MB".format(bytes / 1_000_000.0)
} else {
    "%.2f GB".format(bytes / 1_000_000_000.0)
}
