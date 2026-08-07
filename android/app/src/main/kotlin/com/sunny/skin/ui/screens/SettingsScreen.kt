package com.sunny.skin.ui.screens

import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.CircularProgressIndicator
import com.sunny.skin.ui.i18n.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sunny.skin.ui.SunnyViewModel
import com.sunny.skin.ui.i18n.SunnyLanguage
import com.sunny.skin.ui.i18n.SunnyLanguageController
import com.sunny.skin.ui.i18n.UntranslatedText
import com.sunny.skin.ui.components.DisclaimerCard
import com.sunny.skin.ui.components.LiquidGlassDialog
import com.sunny.skin.ui.components.SectionHeader
import com.sunny.skin.ui.components.SunnyToggle
import com.sunny.skin.ui.components.SunnyCard
import com.sunny.skin.ui.components.SunnyVaultAsset
import com.sunny.skin.ui.components.VaultAssetKind
import com.sunny.skin.ui.theme.SunnyColors
import com.sunny.skin.ui.theme.SunnyMotion
import com.sunny.skin.ui.theme.rememberSunnyMotionEnabled
import kotlinx.coroutines.delay

private enum class BackupExportPhase { EDITING, PREPARING, READY }

@Composable
fun SettingsScreen(
    vm: SunnyViewModel,
    contentPadding: PaddingValues,
    onOpenModelSetup: () -> Unit,
    onSetupPin: () -> Unit,
    onChangePin: () -> Unit,
    onOpenPrivacy: () -> Unit,
) {
    val pinOn by vm.pinEnabled.collectAsStateWithLifecycle()
    val improve by vm.improveSunny.collectAsStateWithLifecycle()
    val contributionStatus by vm.contributionStatus.collectAsStateWithLifecycle()
    val useServer by vm.useServerInference.collectAsStateWithLifecycle()
    val language by SunnyLanguageController.selection.collectAsStateWithLifecycle()
    val languageModelState by SunnyLanguageController.modelState.collectAsStateWithLifecycle()
    val modelStatus by com.sunny.skin.inference.download.ModelDownloadManager.status
        .collectAsStateWithLifecycle()
    val cloudAuthorization by com.sunny.skin.subscription.SubscriptionEntitlements.cloudInference
        .collectAsStateWithLifecycle()
    val context = LocalContext.current
    val motionEnabled = rememberSunnyMotionEnabled()
    var showContributionConsent by remember { mutableStateOf(false) }
    var showCloudConsent by remember { mutableStateOf(false) }
    var showExport by remember { mutableStateOf(false) }
    var showDeleteAll by remember { mutableStateOf(false) }
    var showDeleteSuccess by remember { mutableStateOf(false) }
    var showLanguagePicker by remember { mutableStateOf(false) }
    val now = System.currentTimeMillis()
    val localInstalled = modelStatus is com.sunny.skin.inference.download.ModelStatus.Ready ||
        com.sunny.skin.inference.ModelProvider.packPresent(
            context,
            com.sunny.skin.inference.tier.SunnyModelTier.SUNNY_MOE,
        )
    val localReady = com.sunny.skin.inference.ModelProvider.localModelAvailable(context)
    val localSupported = com.sunny.skin.inference.ModelProvider.localDeviceSupported()
    val hasProAccess = com.sunny.skin.subscription.SubscriptionEntitlements.accessEntitlement()
        .effectivePlan(now) == com.sunny.skin.inference.tier.SunnyPlan.PRO
    val cloudReady = cloudAuthorization?.isValid(now) == true ||
        com.sunny.skin.inference.ModelProvider.serverAvailable(context)

    LaunchedEffect(showDeleteSuccess) {
        if (showDeleteSuccess) {
            delay(1_200)
            showDeleteSuccess = false
        }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .padding(horizontal = 16.dp)
            .padding(top = 16.dp, bottom = 24.dp + contentPadding.calculateBottomPadding()),
    ) {
        Text("Settings", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(20.dp))

        SectionHeader("Privacy & Security")
        DeviceVaultStatus(
            pinEnabled = pinOn,
        )
        Spacer(Modifier.height(8.dp))
        SunnyCard {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                IconBadge(Icons.Filled.Lock, SunnyColors.Orange, iconSize = 30.dp)
                Spacer(Modifier.size(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("App Lock (PIN)", style = MaterialTheme.typography.titleMedium)
                    Text("Require a PIN to open Sunny", style = MaterialTheme.typography.bodyMedium,
                        color = SunnyColors.TextSecondary)
                }
                SunnyToggle(
                    checked = pinOn,
                    onCheckedChange = { on -> if (on) onSetupPin() else vm.clearPin() },
                    accessibilityLabel = "App lock",
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Text("When enabled, you will need to enter your PIN each time you open Sunny. " +
            com.sunny.skin.AppMode.dataPrivacySubtitle(useServer),
            style = MaterialTheme.typography.bodyMedium, color = SunnyColors.TextSecondary,
            modifier = Modifier.padding(horizontal = 4.dp))
        Column(
            Modifier.animateContentSize(
                animationSpec = if (motionEnabled) {
                    tween(SunnyMotion.StateMillis, easing = SunnyMotion.EaseOut)
                } else {
                    snap()
                },
            ),
        ) {
            AnimatedVisibility(
                visible = pinOn,
                enter = fadeIn(tween(SunnyMotion.StateMillis, easing = SunnyMotion.EaseOut)),
                exit = fadeOut(tween(SunnyMotion.StateMillis, easing = SunnyMotion.EaseOut)),
            ) {
                Column {
                    Spacer(Modifier.height(8.dp))
                    SunnyCard(onClick = onChangePin) {
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            IconBadge(Icons.Filled.Lock, SunnyColors.TextPrimary)
                            Spacer(Modifier.size(12.dp))
                            Text("Change PIN", style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.weight(1f))
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null,
                                tint = SunnyColors.TextTertiary)
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(20.dp))

        SectionHeader("Your data")
        SunnyCard {
            Column(Modifier.fillMaxWidth()) {
                Row(
                    Modifier.fillMaxWidth()
                        .clickable(role = Role.Button) { showExport = true }
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconBadge(Icons.Filled.Download, SunnyColors.TextPrimary)
                    Spacer(Modifier.size(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Encrypted backup", style = MaterialTheme.typography.titleMedium)
                        Text("Export scans, notes, reminders and reports",
                            style = MaterialTheme.typography.bodyMedium, color = SunnyColors.TextSecondary)
                    }
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = SunnyColors.TextTertiary)
                }
                HorizontalDivider(color = SunnyColors.Divider)
                Row(
                    Modifier.fillMaxWidth()
                        .clickable(role = Role.Button) { showDeleteAll = true }
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconBadge(Icons.Filled.DeleteForever, SunnyColors.Danger)
                    Spacer(Modifier.size(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Delete all local data", style = MaterialTheme.typography.titleMedium,
                            color = SunnyColors.Danger)
                        Text("Remove every scan, reminder and report",
                            style = MaterialTheme.typography.bodyMedium, color = SunnyColors.TextSecondary)
                    }
                }
            }
        }
        AnimatedVisibility(
            visible = showDeleteSuccess,
            enter = fadeIn(tween(SunnyMotion.StateMillis, easing = SunnyMotion.EaseOut)) +
                if (motionEnabled) {
                    expandVertically(
                        animationSpec = tween(
                            SunnyMotion.StateMillis,
                            easing = SunnyMotion.EaseOut,
                        ),
                    )
                } else {
                    EnterTransition.None
                },
            exit = fadeOut(tween(SunnyMotion.StateMillis, easing = SunnyMotion.EaseOut)) +
                if (motionEnabled) {
                    shrinkVertically(
                        animationSpec = tween(
                            SunnyMotion.StateMillis,
                            easing = SunnyMotion.EaseOut,
                        ),
                    )
                } else {
                    ExitTransition.None
                },
        ) {
            Row(
                Modifier.fillMaxWidth()
                    .padding(top = 8.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(SunnyColors.OrangeSoft.copy(alpha = 0.72f))
                    .clearAndSetSemantics {
                        liveRegion = LiveRegionMode.Polite
                        contentDescription = "All local health data deleted. Device vault is empty."
                    }
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SunnyVaultAsset(
                    kind = VaultAssetKind.CLEARED,
                    modifier = Modifier.size(32.dp),
                )
                Spacer(Modifier.size(10.dp))
                Column {
                    Text(
                        "Local data deleted",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        "Your device vault is empty.",
                        style = MaterialTheme.typography.bodySmall,
                        color = SunnyColors.TextSecondary,
                    )
                }
            }
        }
        Spacer(Modifier.height(20.dp))

        SectionHeader("Analysis")
        AnalysisPreferenceCard(
            cloudSelected = useServer,
            cloudReady = cloudReady,
            localInstalled = localInstalled,
            localReady = localReady,
            localSupported = localSupported,
            hasProAccess = hasProAccess,
            onSelectCloud = {
                if (!useServer) showCloudConsent = true
            },
            onSelectLocal = {
                vm.setUseServerInference(false)
                if (!localReady) onOpenModelSetup()
            },
            onManageCurrent = onOpenModelSetup,
        )
        Spacer(Modifier.height(20.dp))

        if (com.sunny.skin.BuildConfig.SUNNY_CONTRIBUTE_URL.isNotBlank()) {
            SectionHeader("Help improve Sunny")
            SunnyCard {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconBadge(Icons.Filled.Science, SunnyColors.Orange, iconSize = 26.dp)
                    Spacer(Modifier.size(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Contribute to improving Sunny",
                            style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Beta: your scans and any corrections are uploaded with your consent to help " +
                                "train Sunny's AI. Off by default — turn it off anytime.",
                            style = MaterialTheme.typography.bodyMedium, color = SunnyColors.TextSecondary,
                        )
                    }
                    Spacer(Modifier.size(12.dp))
                    SunnyToggle(
                        checked = improve,
                        onCheckedChange = { enabled ->
                            if (enabled) showContributionConsent = true
                            else vm.setImproveSunny(false)
                        },
                        accessibilityLabel = "Contribute scans to improve Sunny",
                    )
                }
            }
            Column(
                Modifier.animateContentSize(
                    animationSpec = if (motionEnabled) {
                        tween(SunnyMotion.StateMillis, easing = SunnyMotion.EaseOut)
                    } else {
                        snap()
                    },
                ),
            ) {
                AnimatedVisibility(
                    visible = improve && contributionStatus != com.sunny.skin.ui.ContributionStatus.IDLE,
                    enter = fadeIn(tween(SunnyMotion.StateMillis, easing = SunnyMotion.EaseOut)),
                    exit = fadeOut(tween(SunnyMotion.StateMillis, easing = SunnyMotion.EaseOut)),
                ) {
                    Crossfade(
                        targetState = contributionStatus,
                        animationSpec = tween(
                            SunnyMotion.StateMillis,
                            easing = SunnyMotion.EaseOut,
                        ),
                        label = "Contribution status",
                    ) { status ->
                        Text(
                            when (status) {
                                com.sunny.skin.ui.ContributionStatus.UPLOADING -> "Sending latest contribution…"
                                com.sunny.skin.ui.ContributionStatus.SENT -> "Latest contribution sent."
                                com.sunny.skin.ui.ContributionStatus.FAILED ->
                                    "Latest contribution could not be sent. Future scans remain enabled."
                                com.sunny.skin.ui.ContributionStatus.IDLE -> ""
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = if (status == com.sunny.skin.ui.ContributionStatus.FAILED) {
                                SunnyColors.Danger
                            } else {
                                SunnyColors.TextSecondary
                            },
                            modifier = Modifier.padding(start = 4.dp, end = 4.dp, top = 6.dp),
                        )
                    }
                }
            }
            Spacer(Modifier.height(20.dp))
        }

        if (showContributionConsent) {
            AlertDialog(
                onDismissRequest = { showContributionConsent = false },
                containerColor = SunnyColors.Surface,
                titleContentColor = SunnyColors.TextPrimary,
                textContentColor = SunnyColors.TextSecondary,
                title = { Text("Contribute beta scans?") },
                text = {
                    Text(
                        "Future saved scans will send the photo, body area, model description, " +
                            "any correction, device model and app version to Sunny's beta " +
                            "contribution server for model improvement. This is separate from " +
                            "inference and can be turned off for future scans at any time."
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        vm.setImproveSunny(true)
                        showContributionConsent = false
                    }) { Text("I agree") }
                },
                dismissButton = {
                    TextButton(onClick = { showContributionConsent = false }) { Text("Cancel") }
                },
            )
        }

        if (showCloudConsent) {
            AlertDialog(
                onDismissRequest = { showCloudConsent = false },
                containerColor = SunnyColors.Surface,
                titleContentColor = SunnyColors.TextPrimary,
                textContentColor = SunnyColors.TextSecondary,
                title = { Text("Use cloud analysis?") },
                text = {
                    Text(
                        "While Cloud is selected, each photo you analyse is sent over HTTPS to " +
                            "Sunny AI Cloud for a visual description. Saved photos and notes remain " +
                            "encrypted on this device. You can switch back to on-device analysis anytime.",
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        vm.setUseServerInference(true)
                        showCloudConsent = false
                    }) { Text("Use cloud analysis") }
                },
                dismissButton = {
                    TextButton(onClick = { showCloudConsent = false }) { Text("Keep on device") }
                },
            )
        }

        if (showExport) {
            var password by remember { mutableStateOf("") }
            var confirmPassword by remember { mutableStateOf("") }
            var exportPhase by remember { mutableStateOf(BackupExportPhase.EDITING) }
            var error by remember { mutableStateOf<String?>(null) }
            var pendingExportAction by remember { mutableStateOf<(() -> Unit)?>(null) }
            LiquidGlassDialog(
                onDismiss = {
                    if (exportPhase != BackupExportPhase.PREPARING) {
                        val action = pendingExportAction
                        pendingExportAction = null
                        showExport = false
                        action?.invoke()
                    }
                },
                dismissEnabled = exportPhase != BackupExportPhase.PREPARING,
            ) { requestDismiss ->
                LaunchedEffect(exportPhase, pendingExportAction) {
                    if (exportPhase == BackupExportPhase.READY && pendingExportAction != null) {
                        delay(220)
                        requestDismiss()
                    }
                }
                Text("Encrypted backup", style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 8.dp))
                Text(
                    "Use a password you can remember. Sunny cannot recover it.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = SunnyColors.TextSecondary,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 22.dp),
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it; error = null },
                    enabled = exportPhase == BackupExportPhase.EDITING,
                    label = { Text("Password") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Next,
                        autoCorrectEnabled = false,
                    ),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 6.dp),
                )
                OutlinedTextField(
                    value = confirmPassword,
                    onValueChange = { confirmPassword = it; error = null },
                    enabled = exportPhase == BackupExportPhase.EDITING,
                    label = { Text("Confirm password") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done,
                        autoCorrectEnabled = false,
                    ),
                    supportingText = {
                        when {
                            password.isNotEmpty() && password.length < 10 -> Text("Use at least 10 characters")
                            confirmPassword.isNotEmpty() && confirmPassword != password -> Text("Passwords do not match")
                            error != null -> Text(error.orEmpty())
                        }
                    },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 22.dp),
                )
                AnimatedVisibility(
                    visible = exportPhase != BackupExportPhase.EDITING,
                    enter = fadeIn(tween(SunnyMotion.StateMillis, easing = SunnyMotion.EaseOut)),
                    exit = fadeOut(tween(SunnyMotion.StateMillis, easing = SunnyMotion.EaseOut)),
                ) {
                    val preparing = exportPhase == BackupExportPhase.PREPARING
                    Row(
                        Modifier.fillMaxWidth()
                            .padding(horizontal = 22.dp, vertical = 8.dp)
                            .clearAndSetSemantics {
                                liveRegion = LiveRegionMode.Polite
                                contentDescription = if (preparing) {
                                    "Preparing encrypted backup"
                                } else {
                                    "Encrypted backup ready. Opening share sheet"
                                }
                            },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (preparing) {
                            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(
                                Icons.Filled.CheckCircle,
                                contentDescription = null,
                                tint = SunnyColors.Success,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                        Spacer(Modifier.size(8.dp))
                        Text(
                            if (preparing) {
                                "Preparing encrypted backup…"
                            } else {
                                "Backup ready — opening share sheet…"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = SunnyColors.TextSecondary,
                        )
                    }
                }
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(
                        onClick = requestDismiss,
                        enabled = exportPhase == BackupExportPhase.EDITING,
                    ) { Text("Cancel") }
                    TextButton(
                        enabled = exportPhase == BackupExportPhase.EDITING &&
                            password.length >= 10 && password == confirmPassword,
                        onClick = {
                            exportPhase = BackupExportPhase.PREPARING
                            val secret = password.toCharArray()
                            password = ""
                            confirmPassword = ""
                            vm.exportEncryptedBackup(secret) { uri, message ->
                                if (uri != null) {
                                    exportPhase = BackupExportPhase.READY
                                    val share = Intent(Intent.ACTION_SEND).apply {
                                        type = "application/octet-stream"
                                        putExtra(Intent.EXTRA_STREAM, uri)
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    pendingExportAction = {
                                        context.startActivity(
                                            Intent.createChooser(share, "Share encrypted backup"),
                                        )
                                    }
                                } else {
                                    exportPhase = BackupExportPhase.EDITING
                                    error = message ?: "Backup could not be created."
                                }
                            }
                        },
                    ) {
                        Text(
                            when (exportPhase) {
                                BackupExportPhase.EDITING -> "Export"
                                BackupExportPhase.PREPARING -> "Preparing…"
                                BackupExportPhase.READY -> "Ready"
                            },
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
        }

        if (showDeleteAll) {
            AlertDialog(
                onDismissRequest = { showDeleteAll = false },
                containerColor = SunnyColors.Surface,
                titleContentColor = SunnyColors.TextPrimary,
                textContentColor = SunnyColors.TextSecondary,
                title = { Text("Delete all local data?") },
                text = {
                    Text(
                        if (com.sunny.skin.AppMode.publicRelease) {
                            "Every scan, photo, size estimate, private note, ABCDE answer, reminder, " +
                                "photo-check session, report and encrypted backup will be permanently " +
                                "removed. This cannot be undone."
                        } else {
                            "Every scan, photo, size estimate, private note, ABCDE answer, reminder, " +
                                "photo-check session, report and cached backup will be permanently " +
                                "removed. Contributions already sent during " +
                                "the beta cannot be deleted from this phone. This cannot be undone."
                        },
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showDeleteAll = false
                            vm.deleteAllLocalData {
                                showDeleteSuccess = true
                            }
                        },
                        colors = ButtonDefaults.textButtonColors(contentColor = SunnyColors.Danger),
                    ) { Text("Delete everything", fontWeight = FontWeight.SemiBold) }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteAll = false }) { Text("Cancel") }
                },
            )
        }

        SectionHeader("Language & region")
        SunnyCard {
            Column(Modifier.padding(horizontal = 16.dp)) {
                AboutRow(
                    Icons.Filled.Language,
                    "App language",
                    language.nativeName,
                    onClick = { showLanguagePicker = true },
                    valueIsAlreadyLocalized = true,
                )
            }
        }
        Spacer(Modifier.height(20.dp))

        if (showLanguagePicker) {
            AlertDialog(
                onDismissRequest = { showLanguagePicker = false },
                containerColor = SunnyColors.Surface,
                title = { Text("Choose language") },
                text = {
                    Column(Modifier.verticalScroll(rememberScrollState())) {
                        SunnyLanguage.entries.forEach { option ->
                            Row(
                                modifier = Modifier.fillMaxWidth().selectable(
                                    selected = language == option,
                                    enabled = languageModelState.preparing == null,
                                    role = Role.RadioButton,
                                    onClick = {
                                        SunnyLanguageController.select(context, option)
                                    },
                                ).padding(vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                RadioButton(
                                    selected = language == option,
                                    onClick = null,
                                )
                                Spacer(Modifier.width(10.dp))
                                UntranslatedText(
                                    option.nativeName,
                                    style = MaterialTheme.typography.bodyLarge,
                                )
                            }
                        }
                        if (languageModelState.preparing != null) {
                            Row(
                                Modifier.fillMaxWidth().padding(vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                                Spacer(Modifier.width(10.dp))
                                Text("Downloading language model…")
                            }
                        }
                        languageModelState.error?.let {
                            Text(
                                "The language model could not be downloaded. Check your connection and try again.",
                                color = SunnyColors.Danger,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showLanguagePicker = false }) { Text("Close") }
                },
            )
        }

        SectionHeader("About")
        SunnyCard {
            Column(Modifier.padding(horizontal = 16.dp)) {
                AboutRow(Icons.Filled.Info, "Version", "1.0 (1)")
                HorizontalDivider(color = SunnyColors.Divider)
                AboutRow(Icons.Filled.Shield, "Privacy Policy", "", onClick = onOpenPrivacy)
            }
        }
        Spacer(Modifier.height(20.dp))

        DisclaimerCard(
            title = "Medical Disclaimer",
            body = "Sunny is a skin tracking tool only. It does not provide medical diagnoses " +
                "or advice. Always consult a qualified healthcare professional for any skin concerns.",
        )
    }

}

@Composable
private fun AnalysisPreferenceCard(
    cloudSelected: Boolean,
    cloudReady: Boolean,
    localInstalled: Boolean,
    localReady: Boolean,
    localSupported: Boolean,
    hasProAccess: Boolean,
    onSelectCloud: () -> Unit,
    onSelectLocal: () -> Unit,
    onManageCurrent: () -> Unit,
) {
    SunnyCard {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (cloudSelected) Icons.Filled.Cloud else Icons.Filled.Memory,
                    contentDescription = null,
                    tint = SunnyColors.OrangeText,
                    modifier = Modifier.size(28.dp),
                )
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(
                        "Where analysis runs",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        "You can change this anytime",
                        style = MaterialTheme.typography.bodySmall,
                        color = SunnyColors.TextSecondary,
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AnalysisModeChoice(
                    modifier = Modifier.weight(1f),
                    selected = cloudSelected,
                    label = "Cloud",
                    onClick = onSelectCloud,
                )
                AnalysisModeChoice(
                    modifier = Modifier.weight(1f),
                    selected = !cloudSelected,
                    label = "On device",
                    enabled = localSupported,
                    onClick = onSelectLocal,
                )
            }
            if (!localSupported) {
                Spacer(Modifier.height(8.dp))
                Text(
                    com.sunny.skin.inference.DeviceInferenceCapabilities.unavailableReason(),
                    style = MaterialTheme.typography.bodySmall,
                    color = SunnyColors.TextSecondary,
                )
            }
            Spacer(Modifier.height(14.dp))
            HorizontalDivider(color = SunnyColors.Divider)
            Row(
                modifier = Modifier.fillMaxWidth()
                    .clickable(role = Role.Button, onClick = onManageCurrent)
                    .padding(top = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        if (cloudSelected) {
                            if (cloudReady) "Cloud analysis is ready" else "Cloud setup needed"
                        } else {
                            if (localReady) "Offline analysis is ready" else "Prepare offline analysis"
                        },
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = if ((cloudSelected && cloudReady) || (!cloudSelected && localReady)) {
                            SunnyColors.TextPrimary
                        } else {
                            SunnyColors.OrangeText
                        },
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        when {
                            cloudSelected && cloudReady ->
                                "Fast and requires no download. Photos are securely sent to Sunny for analysis."
                            cloudSelected ->
                                "Connecting securely to Sunny AI Cloud."
                            localReady ->
                                "Works without internet and keeps analysis on this phone."
                            localInstalled && !hasProAccess ->
                                "The offline model is installed. A Pro plan is required to use it."
                            hasProAccess ->
                                "Included with the app. Prepare once, then use without internet."
                            else ->
                                "Included with the app and available to use with Pro."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = SunnyColors.TextSecondary,
                    )
                }
                Spacer(Modifier.width(8.dp))
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = SunnyColors.TextTertiary,
                )
            }
        }
    }
}

@Composable
private fun AnalysisModeChoice(
    modifier: Modifier,
    selected: Boolean,
    label: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) SunnyColors.OrangeSoft else SunnyColors.SurfaceMuted)
            .selectable(
                selected = selected,
                enabled = enabled,
                role = Role.RadioButton,
                onClick = onClick,
            )
            .padding(horizontal = 12.dp, vertical = 11.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            color = when {
                !enabled -> SunnyColors.TextTertiary
                selected -> SunnyColors.OrangeText
                else -> SunnyColors.TextSecondary
            },
        )
    }
}

@Composable
private fun IconBadge(
    icon: ImageVector,
    tint: androidx.compose.ui.graphics.Color,
    iconSize: androidx.compose.ui.unit.Dp = 24.dp,
) {
    // No background box — just the icon, kept in a 36dp slot so rows stay aligned.
    Box(Modifier.size(36.dp), contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(iconSize))
    }
}

@Composable
private fun DeviceVaultStatus(
    pinEnabled: Boolean,
) {
    val lockStatus = if (pinEnabled) "PIN on" else "PIN optional"
    SunnyCard {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SunnyVaultAsset(
                    kind = VaultAssetKind.VAULT,
                    modifier = Modifier.size(32.dp),
                )
                Spacer(Modifier.size(8.dp))
                Column {
                    Text(
                        "Device vault",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        "Your protection settings at a glance",
                        style = MaterialTheme.typography.bodySmall,
                        color = SunnyColors.TextSecondary,
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
            Row(
                modifier = Modifier.fillMaxWidth().clearAndSetSemantics {
                    contentDescription =
                        "Device vault: $lockStatus and encrypted local storage"
                },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                VaultStatusNode(
                    asset = VaultAssetKind.LOCK,
                    label = lockStatus,
                    active = pinEnabled,
                    modifier = Modifier.weight(1f),
                )
                VaultConnector()
                VaultStatusNode(
                    asset = VaultAssetKind.ENCRYPTED,
                    label = "Encrypted local",
                    active = true,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun VaultStatusNode(
    asset: VaultAssetKind,
    label: String,
    active: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        SunnyVaultAsset(kind = asset, active = active, modifier = Modifier.size(42.dp))
        Spacer(Modifier.height(6.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = SunnyColors.TextSecondary,
            textAlign = TextAlign.Center,
            maxLines = 2,
        )
    }
}

@Composable
private fun VaultConnector() {
    Box(Modifier.size(width = 18.dp, height = 2.dp).background(SunnyColors.OrangeLight))
}

@Composable
private fun AboutRow(
    icon: ImageVector,
    label: String,
    value: String,
    onClick: (() -> Unit)? = null,
    valueIsAlreadyLocalized: Boolean = false,
) {
    Row(
        Modifier.fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = SunnyColors.TextPrimary, modifier = Modifier.size(20.dp))
        Spacer(Modifier.size(12.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f))
        if (value.isNotEmpty()) {
            if (valueIsAlreadyLocalized) {
                UntranslatedText(
                    value,
                    style = MaterialTheme.typography.bodyLarge,
                    color = SunnyColors.TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.End,
                    modifier = Modifier.weight(1f),
                )
            } else {
                Text(
                    value,
                    style = MaterialTheme.typography.bodyLarge,
                    color = SunnyColors.TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.End,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        if (onClick != null) {
            Spacer(Modifier.size(6.dp))
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = SunnyColors.TextTertiary,
                modifier = Modifier.size(20.dp))
        }
    }
}
