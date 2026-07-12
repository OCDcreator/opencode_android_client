package com.yage.opencode_client

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.StringRes
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.*
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import com.yage.opencode_client.ui.MainViewModel
import com.yage.opencode_client.ui.chat.ChatScreen
import com.yage.opencode_client.ui.files.FilesScreen
import com.yage.opencode_client.ui.files.FilesViewModel
import com.yage.opencode_client.ui.session.SessionList
import com.yage.opencode_client.ui.settings.SettingsScreen
import com.yage.opencode_client.ui.theme.OpenCodeTheme
import com.yage.opencode_client.ui.theme.compactTypography
import com.yage.opencode_client.ui.theme.ProvideScaledDpDensity
import com.yage.opencode_client.util.LanguageMode
import com.yage.opencode_client.util.ThemeMode
import com.yage.opencode_client.util.wrapWithLanguage
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

sealed class Screen(
    val route: String,
    @param:StringRes val titleRes: Int,
    val selectedIcon: androidx.compose.ui.graphics.vector.ImageVector,
    val unselectedIcon: androidx.compose.ui.graphics.vector.ImageVector
) {
    object Chat : Screen(
        "chat",
        R.string.nav_chat,
        Icons.AutoMirrored.Filled.Chat,
        Icons.Outlined.ChatBubbleOutline
    )

    object Files : Screen(
        "files",
        R.string.nav_files,
        Icons.Default.Folder,
        Icons.Outlined.Folder
    )

    object Settings : Screen(
        "settings",
        R.string.nav_settings,
        Icons.Default.Settings,
        Icons.Outlined.Settings
    )
}

val screens = listOf(Screen.Chat, Screen.Files, Screen.Settings)

@OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(newBase.wrapWithLanguage())
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val viewModel: MainViewModel = hiltViewModel()
            val lifecycleOwner = LocalLifecycleOwner.current
            LaunchedEffect(lifecycleOwner) {
                lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                    viewModel.testConnection()
                }
            }
            LaunchedEffect(Unit) {
                viewModel.recreateEvent.collect {
                    recreate()
                }
            }
            val state by viewModel.state.collectAsStateWithLifecycle()
            val darkTheme = when (state.themeMode) {
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
            }
            val windowSizeClass = calculateWindowSizeClass(this)
            val isTablet = windowSizeClass.widthSizeClass == WindowWidthSizeClass.Expanded

            OpenCodeTheme(
                darkTheme = darkTheme,
                fontSizeScale = state.fontSizeScale,
                uiScale = state.uiScale
            ) {
                if (isTablet) {
                    TabletLayout(viewModel = viewModel)
                } else {
                    PhoneLayout(viewModel = viewModel)
                }
            }
        }
    }
}

@Composable
private fun PhoneLayout(viewModel: MainViewModel) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    fun navigateToTopLevel(route: String) {
        if (currentRoute == route) return
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets.statusBars,
        bottomBar = {
            ProvideScaledDpDensity {
                NavigationBar {
                    screens.forEach { screen ->
                        val selected = currentRoute == screen.route
                        NavigationBarItem(
                            selected = selected,
                            onClick = { navigateToTopLevel(screen.route) },
                            icon = {
                                Icon(
                                    if (selected) screen.selectedIcon else screen.unselectedIcon,
                                    contentDescription = stringResource(screen.titleRes)
                                )
                            },
                            label = { Text(stringResource(screen.titleRes)) }
                        )
                    }
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Chat.route,
            modifier = Modifier.padding(padding).consumeWindowInsets(padding)
        ) {
            composable(Screen.Chat.route) {
                ChatScreen(
                    viewModel = viewModel,
                    onNavigateToFiles = { path ->
                        viewModel.showFileInFiles(path, originRoute = Screen.Chat.route)
                        navigateToTopLevel(Screen.Files.route)
                    },
                    onNavigateToSettings = {
                        navigateToTopLevel(Screen.Settings.route)
                    },
                    showSettingsButton = false
                )
            }
            composable(Screen.Files.route) {
                val state by viewModel.state.collectAsStateWithLifecycle()
                val filesViewModel: FilesViewModel = hiltViewModel()
                FilesScreen(
                    viewModel = filesViewModel,
                    pathToShow = state.filePathToShowInFiles,
                    sessionDirectory = state.currentSession?.directory,
                    workingDirectory = state.workingDirectory,
                    onCloseFile = {
                        val origin = state.filePreviewOriginRoute
                        viewModel.clearFileToShow()
                        if (origin == Screen.Chat.route) {
                            navigateToTopLevel(Screen.Chat.route)
                        }
                    },
                    onFileClick = { }
                )
            }
            composable(Screen.Settings.route) {
                SettingsScreen(viewModel = viewModel)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TabletLayout(viewModel: MainViewModel) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var sessionsPaneCollapsed by rememberSaveable { mutableStateOf(false) }
    val onOpenSettings: () -> Unit = { selectedTab = 1 }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val filesWeight = if (sessionsPaneCollapsed) 0.5f else 0.375f
    val chatWeight = if (sessionsPaneCollapsed) 0.5f else 0.375f

        Row(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.statusBars)
        ) {
        // Left panel: Session list or Settings — 25% when expanded; hidden when collapsed.
        if (!sessionsPaneCollapsed) {
            Column(
                modifier = Modifier
                    .weight(0.25f)
                    .fillMaxHeight()
            ) {
                if (selectedTab == 1) {
                    SettingsScreen(
                        viewModel = viewModel,
                        onBack = { selectedTab = 0 }
                    )
                } else {
                    SessionList(
                        sessions = state.sessions,
                        currentSessionId = state.currentSessionId,
                        sessionStatuses = state.sessionStatuses,
                        hasMoreSessions = state.hasMoreSessions,
                        isLoadingMoreSessions = state.isLoadingMoreSessions,
                        expandedSessionIds = state.expandedSessionIds,
                        onSelectSession = { viewModel.selectSession(it) },
                        onCreateSession = { viewModel.createSession() },
                        onDeleteSession = { viewModel.deleteSession(it) },
                        onLoadMoreSessions = { viewModel.loadMoreSessions() },
                        onToggleSessionExpanded = { viewModel.toggleSessionExpanded(it) },
                        onOpenSettings = { selectedTab = 1 },
                        onCollapseSessions = { sessionsPaneCollapsed = true },
                        onArchiveSession = { viewModel.archiveSession(it) },
                        onRestoreSession = { viewModel.restoreSession(it) }
                    )
                }
            }

            VerticalDivider()
        }

        // Middle panel: FilesScreen (file preview) — 37.5%, or 50% when Sessions is collapsed.
        Column(
            modifier = Modifier
                .weight(filesWeight)
                .fillMaxHeight()
        ) {
            MaterialTheme(
                colorScheme = MaterialTheme.colorScheme,
                typography = compactTypography(MaterialTheme.typography)
            ) {
                val filesViewModel: FilesViewModel = hiltViewModel()
                Box(modifier = Modifier.fillMaxSize()) {
                    FilesScreen(
                        viewModel = filesViewModel,
                        pathToShow = state.filePathToShowInFiles,
                        sessionDirectory = state.currentSession?.directory,
                        workingDirectory = state.workingDirectory,
                        onCloseFile = { viewModel.clearFileToShow() },
                        onFileClick = { }
                    )
                    if (sessionsPaneCollapsed) {
                        Surface(
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(4.dp),
                            shape = MaterialTheme.shapes.small,
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                            tonalElevation = 3.dp
                        ) {
                            IconButton(onClick = { sessionsPaneCollapsed = false }) {
                                Icon(
                                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                    contentDescription = stringResource(R.string.show_sessions_pane)
                                )
                            }
                        }
                    }
                }
            }
        }

        VerticalDivider()

        // Right panel: Chat — 37.5%, or 50% when Sessions is collapsed.
        Column(
            modifier = Modifier
                .weight(chatWeight)
                .fillMaxHeight()
        ) {
            MaterialTheme(
                colorScheme = MaterialTheme.colorScheme,
                typography = compactTypography(MaterialTheme.typography)
            ) {
                ChatScreen(
                    viewModel = viewModel,
                    onNavigateToFiles = { path ->
                        viewModel.showFileInFiles(path)
                    },
                    onNavigateToSettings = onOpenSettings,
                    showSettingsButton = false,
                    showNewSessionInTopBar = false,
                    showSessionListInTopBar = false
                )
            }
        }
    }
}
