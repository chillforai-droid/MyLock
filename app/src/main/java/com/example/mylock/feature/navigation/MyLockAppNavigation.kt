package com.example.mylock.feature.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ListAlt
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.mylock.core.localization.LocalStrings
import com.example.mylock.core.model.AppLockState
import com.example.mylock.domain.MyLockUiState
import com.example.mylock.domain.MyLockViewModel
import com.example.mylock.feature.authentication.AppLockGateScreen
import com.example.mylock.feature.authentication.AuthenticationScreen
import com.example.mylock.feature.compatibility.CompatibilityScreen
import com.example.mylock.feature.dashboard.DashboardScreen
import com.example.mylock.feature.enrollment.EnrollmentScreen
import com.example.mylock.feature.logs.LogsScreen
import com.example.mylock.feature.settings.SettingsScreen
import kotlinx.serialization.Serializable

@Serializable data object DashboardRoute
@Serializable data object EnrollRoute
@Serializable data object VerifyRoute
@Serializable data object LogsRoute
@Serializable data object SettingsRoute
@Serializable data object CompatibilityRoute

private data class TopLevelDestination(
    val route: Any,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyLockRootHost(
    state: MyLockUiState,
    viewModel: MyLockViewModel,
    modifier: Modifier = Modifier
) {
    if (state.appLockState == AppLockState.LOCKED_APP_GATE) {
        AppLockGateScreen(
            state = state,
            onStartVoiceVerify = viewModel::startVerificationCapture,
            onStopVoiceVerify = viewModel::stopAndVerifySpeaker,
            onLaunchSystemBiometric = viewModel::launchSystemBiometricFallback,
            onVerifyBackupPin = viewModel::verifyBackupPinForUnlock,
            modifier = modifier
        )
        return
    }

    val strings = LocalStrings.current
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination

    val topDestinations = listOf(
        TopLevelDestination(
            route = DashboardRoute,
            label = strings.navDashboard,
            selectedIcon = Icons.Filled.Dashboard,
            unselectedIcon = Icons.Outlined.Dashboard,
            testTag = "nav_dashboard"
        ),
        TopLevelDestination(
            route = EnrollRoute,
            label = strings.navEnroll,
            selectedIcon = Icons.Filled.Mic,
            unselectedIcon = Icons.Outlined.Mic,
            testTag = "nav_enroll"
        ),
        TopLevelDestination(
            route = VerifyRoute,
            label = strings.navVerify,
            selectedIcon = Icons.Filled.GraphicEq,
            unselectedIcon = Icons.Outlined.GraphicEq,
            testTag = "nav_verify"
        ),
        TopLevelDestination(
            route = LogsRoute,
            label = strings.navLogs,
            selectedIcon = Icons.AutoMirrored.Filled.ListAlt,
            unselectedIcon = Icons.AutoMirrored.Filled.ListAlt,
            testTag = "nav_logs"
        ),
        TopLevelDestination(
            route = SettingsRoute,
            label = strings.navSettings,
            selectedIcon = Icons.Filled.Settings,
            unselectedIcon = Icons.Outlined.Settings,
            testTag = "nav_settings"
        )
    )

    val isCompatibilityRoute = currentDestination?.hasRoute<CompatibilityRoute>() == true

    fun navigateTopLevel(route: Any) {
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val useNavigationRail = maxWidth >= 600.dp

        Scaffold(
            contentWindowInsets = WindowInsets.safeDrawing,
            topBar = {
                if (isCompatibilityRoute) {
                    TopAppBar(
                        title = { Text(strings.compatTitle) },
                        navigationIcon = {
                            IconButton(onClick = { navController.popBackStack() }) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Back"
                                )
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        )
                    )
                }
            },
            bottomBar = {
                if (!useNavigationRail && !isCompatibilityRoute) {
                    NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                        topDestinations.forEach { dest ->
                            val selected = currentDestination?.hierarchy?.any {
                                it.hasRoute(dest.route::class)
                            } == true
                            NavigationBarItem(
                                selected = selected,
                                onClick = { navigateTopLevel(dest.route) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                        contentDescription = dest.label
                                    )
                                },
                                label = { Text(dest.label, maxLines = 1) },
                                modifier = Modifier.testTag(dest.testTag)
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (useNavigationRail && !isCompatibilityRoute) {
                    NavigationRail(containerColor = MaterialTheme.colorScheme.surface) {
                        topDestinations.forEach { dest ->
                            val selected = currentDestination?.hierarchy?.any {
                                it.hasRoute(dest.route::class)
                            } == true
                            NavigationRailItem(
                                selected = selected,
                                onClick = { navigateTopLevel(dest.route) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                        contentDescription = dest.label
                                    )
                                },
                                label = { Text(dest.label, maxLines = 1) },
                                modifier = Modifier.testTag(dest.testTag)
                            )
                        }
                    }
                }

                Box(modifier = Modifier.weight(1f).fillMaxSize()) {
                    NavHost(
                        navController = navController,
                        startDestination = DashboardRoute
                    ) {
                        composable<DashboardRoute> {
                            DashboardScreen(
                                state = state,
                                onNavigateEnroll = { navigateTopLevel(EnrollRoute) },
                                onNavigateVerify = { navigateTopLevel(VerifyRoute) },
                                onNavigateSettings = { navigateTopLevel(SettingsRoute) },
                                onNavigateLogs = { navigateTopLevel(LogsRoute) },
                                onNavigateCompatibility = { navController.navigate(CompatibilityRoute) },
                                onTestAppLockGate = { viewModel.lockAppGate() }
                            )
                        }
                        composable<EnrollRoute> {
                            EnrollmentScreen(
                                state = state,
                                onAcceptPrivacy = viewModel::acceptEnrollmentPrivacy,
                                onMicPermissionResult = viewModel::onMicrophonePermissionResult,
                                onPhraseChange = viewModel::updateEnrollmentPhrase,
                                onStartRecording = viewModel::startEnrollmentRecording,
                                onStopRecording = viewModel::stopEnrollmentRecording,
                                onReEnroll = { viewModel.startFreshEnrollment(reEnroll = true) },
                                onDeleteProfile = viewModel::deleteVoiceProfile,
                                onNavigateVerify = { navigateTopLevel(VerifyRoute) }
                            )
                        }
                        composable<VerifyRoute> {
                            AuthenticationScreen(
                                state = state,
                                onRefreshChallenge = viewModel::issueNewVerificationChallenge,
                                onStartCapture = viewModel::startVerificationCapture,
                                onStopAndVerify = viewModel::stopAndVerifySpeaker,
                                onLaunchSystemBiometric = viewModel::launchSystemBiometricFallback,
                                onVerifyBackupPin = viewModel::verifyBackupPinForUnlock,
                                onNavigateEnroll = { navigateTopLevel(EnrollRoute) }
                            )
                        }
                        composable<LogsRoute> {
                            LogsScreen(
                                state = state,
                                onClearLogs = viewModel::clearSecurityLogs
                            )
                        }
                        composable<SettingsRoute> {
                            SettingsScreen(
                                state = state,
                                onSetVoiceAuthEnabled = viewModel::setVoiceAuthEnabled,
                                onSetCustomPhrase = viewModel::setCustomPhrase,
                                onSetLanguage = viewModel::setLanguage,
                                onSetTheme = viewModel::setThemePreference,
                                onSetMicSensitivity = viewModel::setMicSensitivity,
                                onSetSecurityLevel = viewModel::setSecurityLevel,
                                onSetOfflineModeOnly = viewModel::setOfflineModeOnly,
                                onSetStoreRawAudio = viewModel::setStoreRawAudio,
                                onSetAuditLogging = viewModel::setAuditLoggingEnabled,
                                onSetAppLockEnabled = viewModel::setAppLockEnabled,
                                onSaveBackupPin = viewModel::saveBackupPin,
                                onDeleteVoiceProfile = viewModel::deleteVoiceProfile,
                                onNavigateEnroll = { reEnroll ->
                                    viewModel.startFreshEnrollment(reEnroll = reEnroll)
                                    navigateTopLevel(EnrollRoute)
                                },
                                onNavigateVerify = { navigateTopLevel(VerifyRoute) },
                                onNavigateLogs = { navigateTopLevel(LogsRoute) },
                                onNavigateCompatibility = { navController.navigate(CompatibilityRoute) }
                            )
                        }
                        composable<CompatibilityRoute> {
                            CompatibilityScreen(
                                state = state,
                                onRefreshCompatibility = viewModel::refreshCompatibility
                            )
                        }
                    }
                }
            }
        }
    }
}
