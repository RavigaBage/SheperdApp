package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.example.presentation.components.PushRevealDrawer
import com.example.presentation.screens.*
import com.example.presentation.viewmodel.ShepherdViewModel
import com.example.ui.theme.MyApplicationTheme
import com.example.preachmode.PreachModeScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                // Instantiate view model with custom factory
                val appViewModel: ShepherdViewModel = viewModel(
                    factory = ShepherdViewModel.provideFactory(application)
                )

                val navController = rememberNavController()

                val rootFolder by appViewModel.rootFolderUri.collectAsState()
                val isOnboarded by appViewModel.isOnboarded.collectAsState()

                // Route automatically on first load based on folder permission
                val startDestination = when {
                    !isOnboarded || rootFolder == null -> "onboarding"
                    else -> "home"
                }

                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentRoute = navBackStackEntry?.destination?.route ?: startDestination
                var isDrawerOpen by remember { mutableStateOf(false) }

                PushRevealDrawer(
                    isOpen = isDrawerOpen && currentRoute != "onboarding",
                    onClose = { isDrawerOpen = false },
                    activeRoute = currentRoute,
                    viewModel = appViewModel,
                    onNavigate = { route ->
                        navController.navigate(route) {
                            popUpTo("home") { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    onLogout = {
                        appViewModel.resetOnboarding()
                        navController.navigate("onboarding") {
                            popUpTo(0) { inclusive = true }
                        }
                    }
                ) {
                    Scaffold(
                        modifier = Modifier
                            .fillMaxSize()
                            .imePadding(),

                        ) { innerPadding ->
                        NavHost(
                            navController = navController,
                            startDestination = startDestination,

                            modifier = Modifier.padding(innerPadding)
                        ) {
                            composable("onboarding") {
                                OnboardingScreen(
                                    onFolderSelected = { uri ->
                                        appViewModel.selectRootFolder(uri)
                                        appViewModel.completeOnboarding()
                                        navController.navigate("home") {
                                            popUpTo("onboarding") { inclusive = true }
                                        }
                                    },
                                    onFinishedSkip = {
                                        appViewModel.completeOnboarding()
                                        navController.navigate("home") {
                                            popUpTo("onboarding") { inclusive = true }
                                        }
                                    }
                                )
                            }

                            composable("home") {
                                HomeScreen(
                                    viewModel = appViewModel,
                                    onOpenDrawer = { isDrawerOpen = true },
                                    onNavigateToFileBrowser = { navController.navigate("file_list") },
                                    onNavigateToAiEditor = { navController.navigate("ai_editor") },
                                    onNavigateToSearch = { navController.navigate("search") },
                                    onNavigateToScripture = { navController.navigate("sermons") },
                                    onNavigateToHistory = { navController.navigate("history") },
                                    onNavigateToSettings = { navController.navigate("settings") },
                                    onNavigate = { route ->
                                        navController.navigate(route) {
                                            popUpTo("home") { saveState = true }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    }
                                )
                            }

                        composable("file_list") {
                            FileListScreen(
                                viewModel = appViewModel,
                                onBack = { navController.popBackStack() },
                                onNavigateToSermonViewer = {
                                    appViewModel.loadDocumentFromUri(
                                        appViewModel.activeViewerSermonId,
                                        appViewModel.activeViewerFilePath,
                                        appViewModel.activeViewerTitle
                                    )
                                    navController.navigate("sermon_viewer")
                                },
                                onNavigate = { route ->
                                    navController.navigate(route) {
                                        popUpTo("home") { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            )
                        }
                        composable("trash") {
                            TrashScreen(
                                viewModel = appViewModel,
                                onBack = { navController.popBackStack() }
                            )
                        }

                        composable("ai_editor") {
                            AiEditorScreen(
                                viewModel = appViewModel,
                                onBack = { navController.popBackStack() },
                                onNavigate = { route ->
                                    navController.navigate(route) {
                                        popUpTo("home") { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            )
                        }

                        composable("search") {
                            SearchScreen(
                                viewModel = appViewModel,
                                onBack = { navController.popBackStack() }
                            )
                        }

                        composable("history") {
                            HistoryScreen(
                                viewModel = appViewModel,
                                onBack = { navController.popBackStack() },
                                onNavigate = { route ->
                                    navController.navigate(route) {
                                        popUpTo("home") { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            )
                        }

                        composable("notes") {
                            com.example.notes.ui.NotebookListScreen(
                                onBack = { navController.popBackStack() },
                                onNavigateToPage = { pageId, notebookId ->
                                    navController.navigate("notes/$notebookId/$pageId")
                                },
                                onNavigateToPreach = { notebookId, title ->
                                    appViewModel.activeViewerSermonId = notebookId
                                    appViewModel.activeViewerFilePath = ""
                                    appViewModel.activeViewerTitle = title
                                    appViewModel.activeViewerIsNote = true
                                    appViewModel.activeViewerIsNotebookScope = true
                                    appViewModel.livePreachDurationMinutes = 30
                                    navController.navigate("preach_mode")
                                }
                            )
                        }

                        composable("notes/{notebookId}/{pageId}") { backStackEntry ->
                            val notebookId = backStackEntry.arguments?.getString("notebookId") ?: return@composable
                            val pageId = backStackEntry.arguments?.getString("pageId") ?: "default-page"
                            val pendingText = backStackEntry.savedStateHandle.get<String>("insert_text")
                            com.example.notes.ui.NotesScreen(
                                pageId = pageId,
                                notebookId = notebookId,
                                pendingInsertText = pendingText,
                                onClearPendingInsertText = {
                                    backStackEntry.savedStateHandle.remove<String>("insert_text")
                                },
                                onBack = { navController.popBackStack() },
                                onNavigateToPreach = { duration ->
                                    appViewModel.activeViewerSermonId = pageId
                                    appViewModel.activeViewerFilePath = ""
                                    appViewModel.activeViewerTitle = "Page Note"
                                    appViewModel.activeViewerIsNote = true
                                    appViewModel.activeViewerIsNotebookScope = false
                                    appViewModel.livePreachDurationMinutes = duration
                                    navController.navigate("preach_mode")
                                },
                                onNavigateToLibrary = {
                                    navController.navigate("illustration_library")
                                }
                            )
                        }

                        composable("illustration_library") {
                            com.example.notes.ui.IllustrationLibraryScreen(
                                onBack = { navController.popBackStack() },
                                onInsertIllustration = { illustration ->
                                    navController.previousBackStackEntry?.savedStateHandle?.set("insert_text", illustration.bodyText)
                                    navController.popBackStack()
                                }
                            )
                        }

                        composable("sermon_ideas") {
                            com.example.notes.ui.SermonIdeaBrowseScreen(
                                onBack = { navController.popBackStack() },
                                onNavigateToPage = { pageId, notebookId ->
                                    navController.navigate("notes/$notebookId/$pageId")
                                }
                            )
                        }

                        composable("library") {
                            com.example.presentation.screens.LibraryScreen(
                                viewModel = appViewModel,
                                onBack = { navController.popBackStack() },
                                onNavigateToSermonViewer = {
                                    appViewModel.loadDocumentFromUri(
                                        appViewModel.activeViewerSermonId,
                                        appViewModel.activeViewerFilePath,
                                        appViewModel.activeViewerTitle
                                    )
                                    navController.navigate("sermon_viewer")
                                },
                                onNavigate = { route ->
                                    navController.navigate(route) {
                                        popUpTo("home") { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            )
                        }

                        composable("sermons") {
                            SermonCalendarScreen(
                                viewModel = appViewModel,
                                onBack = { navController.popBackStack() },
                                onNavigate = { route ->
                                    navController.navigate(route) {
                                        popUpTo("home") { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            )
                        }

                        composable("sermon_viewer") {
                            SermonViewerScreen(
                                viewModel = appViewModel,
                                sermonId = appViewModel.activeViewerSermonId,
                                filePath = appViewModel.activeViewerFilePath,
                                sermonTitle = appViewModel.activeViewerTitle,
                                onBack = { navController.popBackStack() },
                                onNavigateToPreach = { id, file, title, duration, speed, scale ->
                                    appViewModel.activeViewerSermonId = id
                                    appViewModel.activeViewerFilePath = file
                                    appViewModel.activeViewerTitle = title
                                    appViewModel.livePreachDurationMinutes = duration
                                    navController.navigate("preach_mode")
                                }
                            )
                        }

                        composable("preach_mode") {
                            PreachModeScreen(
                                viewModel = appViewModel,
                                filePath = appViewModel.activeViewerFilePath,
                                sermonTitle = appViewModel.activeViewerTitle,
                                durationMinutes = appViewModel.livePreachDurationMinutes,
                                isNote = appViewModel.activeViewerIsNote,
                                isNotebookScope = appViewModel.activeViewerIsNotebookScope,
                                sermonId = appViewModel.activeViewerSermonId,
                                onBack = { navController.popBackStack() }
                            )
                        }

                        composable("sermon_calendar") {
                            SermonCalendarScreen(
                                viewModel = appViewModel,
                                onBack = { navController.popBackStack() },
                                onNavigate = { route ->
                                    navController.navigate(route) {
                                        popUpTo("home") { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            )
                        }

                        composable("settings") {
                            SettingsScreen(
                                viewModel = appViewModel,
                                onBack = { navController.popBackStack() }
                            )
                        }
                    }
                }
            }
            }
        }
    }
}
