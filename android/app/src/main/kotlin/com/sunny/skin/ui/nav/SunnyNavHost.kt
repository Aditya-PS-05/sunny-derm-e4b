package com.sunny.skin.ui.nav

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.sunny.skin.ui.SunnyViewModel
import com.sunny.skin.ui.screens.BodyGuideScreen
import com.sunny.skin.ui.screens.CameraScreen
import com.sunny.skin.ui.screens.CaptureScreen
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

@Composable
fun SunnyNavHost(vm: SunnyViewModel = viewModel()) {
    val nav = rememberNavController()
    val modelAvailable by vm.modelAvailable.collectAsStateWithLifecycle()
    val backStack by nav.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route
    val showBar = currentRoute in Routes.topLevel

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

    Scaffold(
        containerColor = androidx.compose.material3.MaterialTheme.colorScheme.background,
        bottomBar = {
            AnimatedVisibility(
                visible = showBar,
                enter = fadeIn() + slideInVertically { it },
                exit = fadeOut() + slideOutVertically { it },
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
        ) {
            composable(Routes.OVERVIEW) {
                OverviewScreen(vm, onScanClick = { nav.navigate(Routes.scanDetail(it)) })
            }
            composable(Routes.SAVED) {
                SavedScreen(
                    vm,
                    contentPadding = padding,
                    onScanClick = { nav.navigate(Routes.scanDetail(it)) },
                    onGenerateReport = { nav.navigate(Routes.GENERATE_REPORT) },
                    onOpenReports = { nav.navigate(Routes.REPORTS) },
                    onOpenReport = { nav.navigate(Routes.reportDetail(it)) },
                )
            }
            composable(Routes.SETTINGS) {
                SettingsScreen(vm, contentPadding = padding,
                    onOpenReports = { nav.navigate(Routes.REPORTS) },
                    onOpenModelSetup = { nav.navigate(Routes.MODEL_SETUP) },
                    onSetupPin = { nav.navigate(Routes.pinSetup(false)) },
                    onChangePin = { nav.navigate(Routes.pinSetup(true)) },
                    onOpenPrivacy = { nav.navigate(Routes.PRIVACY) })
            }
            composable(Routes.MODEL_SETUP) {
                ModelSetupScreen(onBack = { nav.popBackStack() })
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

            captureGraph(nav, vm, modelAvailable)

            composable(Routes.SCAN_DETAIL) { entry ->
                val scanId = entry.arguments?.getString("scanId").orEmpty()
                ScanDetailScreen(vm, scanId, onBack = { nav.popBackStack() },
                    onEdit = { nav.navigate(Routes.editScan(scanId)) },
                    onCompare = { nav.navigate(Routes.compare(scanId)) })
            }
            composable(Routes.COMPARE) { entry ->
                val scanId = entry.arguments?.getString("scanId").orEmpty()
                CompareScreen(vm, scanId, onBack = { nav.popBackStack() })
            }
            composable(Routes.EDIT_SCAN) { entry ->
                val scanId = entry.arguments?.getString("scanId").orEmpty()
                EditScanScreen(vm, scanId, onDone = { nav.popBackStack() })
            }
            composable(Routes.REPORTS) {
                ReportsScreen(onBack = { nav.popBackStack() },
                    onReportClick = { nav.navigate(Routes.reportDetail(it)) })
            }
            composable(Routes.REPORT_DETAIL) { entry ->
                val reportId = entry.arguments?.getString("reportId").orEmpty()
                ReportDetailScreen(reportId, onBack = { nav.popBackStack() })
            }
        }
    }
}

private fun NavGraphBuilder.captureGraph(
    nav: androidx.navigation.NavHostController,
    vm: SunnyViewModel,
    modelAvailable: Boolean,
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
            ModelSetupScreen(onBack = { nav.popBackStack() })
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
            onSaved = {
                nav.navigate(Routes.SAVED) {
                    popUpTo(Routes.OVERVIEW)
                    launchSingleTop = true
                }
            },
            onDiscard = { nav.popBackStack(Routes.OVERVIEW, inclusive = false) },
        )
    }
    composable(Routes.GENERATE_REPORT) {
        GenerateReportScreen(vm, onDismiss = { nav.popBackStack() },
            onOpenReport = { id ->
                nav.navigate(Routes.reportDetail(id)) {
                    popUpTo(Routes.GENERATE_REPORT) { inclusive = true }
                }
            })
    }
}
