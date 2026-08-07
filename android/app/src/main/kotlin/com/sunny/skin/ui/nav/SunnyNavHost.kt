package com.sunny.skin.ui.nav

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.AlertDialog
import com.sunny.skin.ui.i18n.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import com.sunny.skin.LaunchRequest
import com.sunny.skin.ui.SunnyViewModel
import com.sunny.skin.ui.screens.BodyGuideScreen
import com.sunny.skin.ui.screens.CameraScreen
import com.sunny.skin.ui.screens.CaptureScreen
import com.sunny.skin.ui.screens.CheckSessionScreen
import com.sunny.skin.ui.screens.CompareScreen
import com.sunny.skin.ui.screens.EditScanScreen
import com.sunny.skin.ui.screens.GenerateReportScreen
import com.sunny.skin.ui.screens.ModelSetupScreen
import com.sunny.skin.ui.screens.OverviewScreen
import com.sunny.skin.ui.screens.PinScreen
import com.sunny.skin.ui.screens.PrivacyScreen
import com.sunny.skin.ui.screens.ReportDetailScreen
import com.sunny.skin.ui.screens.ReportsScreen
import com.sunny.skin.ui.screens.ReviewScanScreen
import com.sunny.skin.ui.screens.SavedScreen
import com.sunny.skin.ui.screens.ScanDetailScreen
import com.sunny.skin.ui.screens.SettingsScreen
import com.sunny.skin.ui.theme.SunnyMotion
import com.sunny.skin.ui.theme.rememberSunnyMotionEnabled
import com.sunny.skin.ui.theme.SunnyColors

private data class ProFeatureRequest(
    val title: String,
    val description: String,
    val destination: String,
)

@Composable
fun SunnyNavHost(
    nav: NavHostController,
    vm: SunnyViewModel = viewModel(),
    launchRequest: LaunchRequest? = null,
    onLaunchRequestHandled: () -> Unit = {},
) {
    var openReminderCenter by remember { mutableStateOf(false) }
    var proFeatureRequest by remember { mutableStateOf<ProFeatureRequest?>(null) }
    var pendingProDestination by remember { mutableStateOf<String?>(null) }
    val modelAvailable by vm.modelAvailable.collectAsStateWithLifecycle()
    val verifiedEntitlement by com.sunny.skin.subscription.SubscriptionEntitlements.current
        .collectAsStateWithLifecycle()
    val now = System.currentTimeMillis()
    val featureEntitlement = if (
        com.sunny.skin.BuildConfig.DEBUG &&
        com.sunny.skin.BuildConfig.SUNNY_ENTITLEMENT_API_URL.isBlank()
    ) {
        com.sunny.skin.subscription.SubscriptionEntitlements.accessEntitlement()
    } else {
        verifiedEntitlement
    }
    val proFeaturesAvailable = featureEntitlement.effectivePlan(now) ==
        com.sunny.skin.inference.tier.SunnyPlan.PRO
    val backStack by nav.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route
    val showBar = currentRoute in Routes.topLevel
    val motionEnabled = rememberSunnyMotionEnabled()

    LaunchedEffect(proFeaturesAvailable, pendingProDestination) {
        val destination = pendingProDestination
        if (proFeaturesAvailable && destination != null) {
            pendingProDestination = null
            nav.navigate(destination) {
                popUpTo(Routes.MODEL_SETUP) { inclusive = true }
                launchSingleTop = true
            }
        }
    }

    fun openTab(route: String) {
        if (route == Routes.OVERVIEW) {
            // Overview is the start destination: popping back to it is deterministic.
            // navigate(OVERVIEW) + restoreState can re-restore the just-saved sibling
            // tab stack and land on the wrong screen.
            nav.popBackStack(Routes.OVERVIEW, inclusive = false)
        } else {
            nav.navigate(route) {
                popUpTo(Routes.OVERVIEW) { saveState = true }
                launchSingleTop = true
                restoreState = true
            }
        }
    }

    fun openReportGenerator() {
        if (proFeaturesAvailable) {
            nav.navigate(Routes.GENERATE_REPORT)
        } else {
            proFeatureRequest = ProFeatureRequest(
                title = "Create shareable visual reports",
                description = "Pro creates encrypted PDF reports with selected photos, dates and visual comparisons.",
                destination = Routes.GENERATE_REPORT,
            )
        }
    }

    LaunchedEffect(launchRequest) {
        launchRequest ?: return@LaunchedEffect
        when {
            launchRequest.scanId != null -> nav.navigate(Routes.scanDetail(launchRequest.scanId)) {
                launchSingleTop = true
            }
            launchRequest.openReminders -> {
                openReminderCenter = true
                openTab(Routes.SAVED)
            }
        }
        onLaunchRequestHandled()
    }

    Scaffold(
        containerColor = androidx.compose.material3.MaterialTheme.colorScheme.background,
        bottomBar = {
            AnimatedVisibility(
                visible = showBar,
                enter = fadeIn(
                    tween(SunnyMotion.ModalEnterMillis, easing = SunnyMotion.DrawerEase),
                ) + if (motionEnabled) {
                    slideInVertically(
                        animationSpec = tween(
                            SunnyMotion.ModalEnterMillis,
                            easing = SunnyMotion.DrawerEase,
                        ),
                        initialOffsetY = { height -> height / 5 },
                    )
                } else {
                    EnterTransition.None
                },
                exit = fadeOut(
                    tween(SunnyMotion.ModalExitMillis, easing = SunnyMotion.DrawerEase),
                ) + if (motionEnabled) {
                    slideOutVertically(
                        animationSpec = tween(
                            SunnyMotion.ModalExitMillis,
                            easing = SunnyMotion.DrawerEase,
                        ),
                        targetOffsetY = { height -> height / 5 },
                    )
                } else {
                    ExitTransition.None
                },
            ) {
                Box(Modifier.navigationBarsPadding()) {
                    SunnyBottomBar(
                        currentRoute = currentRoute,
                        onSelectTab = ::openTab,
                        onCapture = {
                            nav.navigate(if (modelAvailable) Routes.CAPTURE else Routes.MODEL_SETUP)
                        },
                    )
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = nav,
            startDestination = Routes.OVERVIEW,
            modifier = Modifier.fillMaxSize(),
            enterTransition = {
                if (switchesTopLevelTabs()) {
                    EnterTransition.None
                } else if (motionEnabled) {
                    fadeIn(
                        tween(SunnyMotion.ScreenEnterMillis, easing = SunnyMotion.EaseOut),
                    ) + slideInHorizontally(
                        animationSpec = tween(
                            SunnyMotion.ScreenEnterMillis,
                            easing = SunnyMotion.EaseOut,
                        ),
                        initialOffsetX = { width -> width * 8 / 100 },
                    )
                } else {
                    fadeIn(
                        tween(SunnyMotion.ScreenExitMillis, easing = SunnyMotion.EaseOut),
                    )
                }
            },
            exitTransition = {
                if (switchesTopLevelTabs()) {
                    ExitTransition.None
                } else {
                    fadeOut(
                        tween(SunnyMotion.ScreenExitMillis, easing = SunnyMotion.EaseOut),
                    )
                }
            },
            popEnterTransition = {
                if (switchesTopLevelTabs()) {
                    EnterTransition.None
                } else {
                    fadeIn(
                        tween(SunnyMotion.ScreenExitMillis, easing = SunnyMotion.EaseOut),
                    )
                }
            },
            popExitTransition = {
                if (switchesTopLevelTabs()) {
                    ExitTransition.None
                } else if (motionEnabled) {
                    fadeOut(
                        tween(SunnyMotion.ScreenExitMillis, easing = SunnyMotion.EaseOut),
                    ) + slideOutHorizontally(
                        animationSpec = tween(
                            SunnyMotion.ScreenExitMillis,
                            easing = SunnyMotion.EaseOut,
                        ),
                        targetOffsetX = { width -> width * 8 / 100 },
                    )
                } else {
                    fadeOut(
                        tween(SunnyMotion.ScreenExitMillis, easing = SunnyMotion.EaseOut),
                    )
                }
            },
        ) {
            composable(Routes.OVERVIEW) {
                OverviewScreen(
                    vm,
                    onScanClick = { nav.navigate(Routes.scanDetail(it)) },
                    onCheckSession = {
                        vm.startCheckSession()
                        nav.navigate(Routes.CHECK_SESSION) { launchSingleTop = true }
                    },
                    onAddPhoto = {
                        nav.navigate(if (modelAvailable) Routes.CAPTURE else Routes.MODEL_SETUP)
                    },
                )
            }
            composable(Routes.SAVED) {
                SavedScreen(
                    vm,
                    contentPadding = padding,
                    onScanClick = { nav.navigate(Routes.scanDetail(it)) },
                    onAddPhoto = {
                        nav.navigate(if (modelAvailable) Routes.CAPTURE else Routes.MODEL_SETUP)
                    },
                    onGenerateReport = ::openReportGenerator,
                    proReportsEnabled = proFeaturesAvailable,
                    onOpenReports = { nav.navigate(Routes.REPORTS) },
                    onOpenReport = { nav.navigate(Routes.reportDetail(it)) },
                    openReminderCenter = openReminderCenter,
                    onReminderCenterOpened = { openReminderCenter = false },
                )
            }
            composable(Routes.SETTINGS) {
                SettingsScreen(vm, contentPadding = padding,
                    onOpenModelSetup = { nav.navigate(Routes.MODEL_SETUP) },
                    onSetupPin = { nav.navigate(Routes.pinSetup(false)) },
                    onChangePin = { nav.navigate(Routes.pinSetup(true)) },
                    onOpenPrivacy = { nav.navigate(Routes.PRIVACY) })
            }
            composable(Routes.MODEL_SETUP) {
                ModelSetupScreen(vm = vm, onBack = {
                    if (!proFeaturesAvailable) pendingProDestination = null
                    nav.popBackStack()
                })
            }
            composable(Routes.PRIVACY) {
                PrivacyScreen(onBack = { nav.popBackStack() })
            }
            composable(Routes.PIN_SETUP) { entry ->
                // Always CREATE mode (existingPin = null) so the user sets a fresh PIN.
                val change = entry.arguments?.getString("change")?.toBoolean() ?: false
                PinScreen(
                    verify = null,
                    onSuccess = { pin -> vm.setPin(pin); nav.popBackStack() },
                    onCancel = { nav.popBackStack() },
                    changeMode = change,
                )
            }

            composable(Routes.CHECK_SESSION) {
                CheckSessionScreen(
                    vm = vm,
                    onBack = { nav.popBackStack() },
                    onCapture = {
                        nav.navigate(if (modelAvailable) Routes.CAMERA else Routes.MODEL_SETUP)
                    },
                    onAddPhoto = {
                        nav.navigate(if (modelAvailable) Routes.CAPTURE else Routes.MODEL_SETUP)
                    },
                )
            }

            captureGraph(
                nav = nav,
                vm = vm,
                modelAvailable = modelAvailable,
                proFeaturesAvailable = proFeaturesAvailable,
                onRequirePro = { proFeatureRequest = it },
            )

            composable(Routes.SCAN_DETAIL) { entry ->
                val scanId = entry.arguments?.getString("scanId").orEmpty()
                ScanDetailScreen(vm, scanId, onBack = { nav.popBackStack() },
                    onEdit = { nav.navigate(Routes.editScan(scanId)) },
                    onCompare = {
                        if (proFeaturesAvailable) nav.navigate(Routes.compare(scanId))
                        else proFeatureRequest = ProFeatureRequest(
                            title = "Compare photos over time",
                            description = "Pro aligns two dated photos and provides fade, wipe, blink and side-by-side comparison tools.",
                            destination = Routes.compare(scanId),
                        )
                    },
                    onOpenAnalysisSetup = { nav.navigate(Routes.MODEL_SETUP) },
                    onCaptureFollowUp = { nav.navigate(Routes.CAMERA) },
                    onReviewFollowUp = { nav.navigate(Routes.REVIEW) })
            }
            composable(Routes.COMPARE) { entry ->
                if (proFeaturesAvailable) {
                    val scanId = entry.arguments?.getString("scanId").orEmpty()
                    CompareScreen(vm, scanId, onBack = { nav.popBackStack() })
                } else {
                    LaunchedEffect(entry) {
                        nav.popBackStack()
                        proFeatureRequest = ProFeatureRequest(
                            title = "Compare photos over time",
                            description = "Pro aligns two dated photos and provides fade, wipe, blink and side-by-side comparison tools.",
                            destination = Routes.compare(entry.arguments?.getString("scanId").orEmpty()),
                        )
                    }
                }
            }
            composable(Routes.EDIT_SCAN) { entry ->
                val scanId = entry.arguments?.getString("scanId").orEmpty()
                EditScanScreen(vm, scanId, onDone = { nav.popBackStack() })
            }
            composable(Routes.REPORTS) {
                ReportsScreen(
                    onBack = { nav.popBackStack() },
                    onReportClick = { nav.navigate(Routes.reportDetail(it)) },
                    onGenerateReport = ::openReportGenerator,
                )
            }
            composable(Routes.REPORT_DETAIL) { entry ->
                val reportId = entry.arguments?.getString("reportId").orEmpty()
                ReportDetailScreen(reportId, onBack = { nav.popBackStack() })
            }
        }
    }

    proFeatureRequest?.let { request ->
        AlertDialog(
            onDismissRequest = { proFeatureRequest = null },
            containerColor = SunnyColors.Surface,
            title = { Text(request.title, style = MaterialTheme.typography.titleLarge) },
            text = {
                Text(
                    request.description + " Your existing photos remain available without Pro.",
                    color = SunnyColors.TextSecondary,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    proFeatureRequest = null
                    pendingProDestination = request.destination
                    nav.navigate(Routes.MODEL_SETUP) { launchSingleTop = true }
                }) { Text("View Pro", color = SunnyColors.OrangeText) }
            },
            dismissButton = {
                TextButton(onClick = { proFeatureRequest = null }) {
                    Text("Not now", color = SunnyColors.TextSecondary)
                }
            },
        )
    }
}

private fun androidx.compose.animation.AnimatedContentTransitionScope<
    androidx.navigation.NavBackStackEntry,
>.switchesTopLevelTabs(): Boolean =
    initialState.destination.route in Routes.topLevel &&
        targetState.destination.route in Routes.topLevel

private fun NavGraphBuilder.captureGraph(
    nav: androidx.navigation.NavHostController,
    vm: SunnyViewModel,
    modelAvailable: Boolean,
    proFeaturesAvailable: Boolean,
    onRequirePro: (ProFeatureRequest) -> Unit,
) {
    composable(Routes.CAPTURE) {
        if (modelAvailable) {
            CaptureScreen(
                vm = vm,
                onOpenCamera = { nav.navigate(Routes.CAMERA) },
                onImageChosen = { nav.navigate(Routes.REVIEW) },
                onGuided = { nav.navigate(Routes.BODY_GUIDE) },
                onBack = { nav.popBackStack() },
            )
        } else {
            ModelSetupScreen(vm = vm, onBack = { nav.popBackStack() })
        }
    }
    composable(Routes.BODY_GUIDE) {
        BodyGuideScreen(
            vm = vm,
            onBack = { nav.popBackStack() },
            onCapturePose = { nav.navigate(Routes.CAMERA) },
        )
    }
    composable(Routes.CAMERA) {
        CameraScreen(
            vm = vm,
            onCaptured = {
                nav.navigate(Routes.REVIEW) {
                    popUpTo(Routes.CAPTURE) { inclusive = true }
                }
            },
            onClose = { nav.popBackStack() },
        )
    }
    composable(Routes.REVIEW) {
        ReviewScanScreen(
            vm = vm,
            onSaved = { scanId, wasRecheck, checkSessionId ->
                if (checkSessionId != null) {
                    if (!nav.popBackStack(Routes.CHECK_SESSION, inclusive = false)) {
                        nav.navigate(Routes.CHECK_SESSION) {
                            popUpTo(Routes.OVERVIEW)
                            launchSingleTop = true
                        }
                    }
                } else if (wasRecheck) {
                    val returnedToDetail = nav.popBackStack(Routes.SCAN_DETAIL, inclusive = false)
                    if (!returnedToDetail) {
                        nav.navigate(Routes.scanDetail(scanId)) {
                            popUpTo(Routes.OVERVIEW)
                            launchSingleTop = true
                        }
                    }
                } else {
                    nav.navigate(Routes.SAVED) {
                        popUpTo(Routes.OVERVIEW)
                        launchSingleTop = true
                    }
                }
            },
            onDiscard = { targetScanId, checkSessionId ->
                if (checkSessionId != null) {
                    if (!nav.popBackStack(Routes.CHECK_SESSION, inclusive = false)) {
                        nav.navigate(Routes.CHECK_SESSION) { launchSingleTop = true }
                    }
                } else if (targetScanId == null) {
                    nav.popBackStack(Routes.OVERVIEW, inclusive = false)
                } else if (!nav.popBackStack(Routes.SCAN_DETAIL, inclusive = false)) {
                    nav.navigate(Routes.scanDetail(targetScanId)) {
                        popUpTo(Routes.OVERVIEW)
                        launchSingleTop = true
                    }
                }
            },
            onRetake = {
                val route = if (vm.capture.value.targetScanId != null) Routes.CAMERA else Routes.CAPTURE
                nav.navigate(route) {
                    popUpTo(Routes.REVIEW) { inclusive = true }
                }
            },
            onOpenAnalysisSetup = { nav.navigate(Routes.MODEL_SETUP) },
        )
    }
    composable(Routes.GENERATE_REPORT) {
        if (proFeaturesAvailable) {
            GenerateReportScreen(vm, onDismiss = { nav.popBackStack() },
                onOpenReport = { id ->
                    nav.navigate(Routes.reportDetail(id)) {
                        popUpTo(Routes.GENERATE_REPORT) { inclusive = true }
                    }
                })
        } else {
            LaunchedEffect(Unit) {
                nav.popBackStack()
                onRequirePro(
                    ProFeatureRequest(
                        title = "Create shareable visual reports",
                        description = "Pro creates encrypted PDF reports with selected photos, dates and visual comparisons.",
                        destination = Routes.GENERATE_REPORT,
                    ),
                )
            }
        }
    }
}
