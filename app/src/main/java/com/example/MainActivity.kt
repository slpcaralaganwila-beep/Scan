package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.data.ScannedDocument
import com.example.ui.PaperScanViewModel
import com.example.ui.screens.DocumentDetailScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.ScanEditorScreen
import com.example.ui.screens.ScannerCameraScreen
import com.example.ui.theme.PaperScanTheme

object Routes {
    const val HOME = "home"
    const val CAMERA = "camera"
    const val EDITOR = "editor"
    const val DETAIL = "detail/{docId}"

    fun detail(docId: String) = "detail/$docId"
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PaperScanTheme {
                PaperScanApp()
            }
        }
    }
}

@Composable
fun PaperScanApp(
    viewModel: PaperScanViewModel = viewModel()
) {
    val navController = rememberNavController()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var pendingSafDoc by remember { mutableStateOf<ScannedDocument?>(null) }
    val createPdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri ->
        val doc = pendingSafDoc
        pendingSafDoc = null
        if (uri != null && doc != null) {
            viewModel.exportPdfToSafUri(doc, uri)
        }
    }

    fun launchSafExport(doc: ScannedDocument) {
        pendingSafDoc = doc
        createPdfLauncher.launch(viewModel.suggestedPdfFileName(doc))
    }

    NavHost(
        navController = navController,
        startDestination = Routes.HOME
    ) {
        composable(Routes.HOME) {
            HomeScreen(
                uiState = uiState,
                onSearchQueryChange = viewModel::setSearchQuery,
                onToggleLanguage = viewModel::toggleLanguage,
                onToggleAutoSaveDownloads = viewModel::setAutoSaveDownloads,
                onStartCameraScan = {
                    viewModel.startNewScanSession()
                    navController.navigate(Routes.CAMERA)
                },
                onImportPhotos = { uris ->
                    viewModel.startNewScanSession()
                    viewModel.importPhotosFromUris(uris) {
                        navController.navigate(Routes.EDITOR)
                    }
                },
                onOpenDocument = { doc ->
                    navController.navigate(Routes.detail(doc.id))
                },
                onSharePdf = { doc ->
                    context.startActivity(viewModel.createSharePdfIntent(doc))
                },
                onShareImages = { doc ->
                    context.startActivity(viewModel.createShareImagesIntent(doc))
                },
                onSaveToDownloads = { doc ->
                    viewModel.savePdfToDownloads(doc) {
                        launchSafExport(doc)
                    }
                },
                onSaveToGallery = { doc ->
                    viewModel.savePagesToGallery(doc) {
                        launchSafExport(doc)
                    }
                },
                onRenameDocument = { doc, newTitle ->
                    viewModel.renameDocument(doc.id, newTitle)
                },
                onDeleteDocument = { doc ->
                    viewModel.deleteDocument(doc.id)
                },
                onClearSnackbar = viewModel::clearSnackbar
            )
        }

        composable(Routes.CAMERA) {
            ScannerCameraScreen(
                language = uiState.language,
                draftCount = uiState.draftPages.size,
                isProcessing = uiState.isProcessing,
                createOutputFile = viewModel::createCameraCaptureFile,
                onPhotoCaptured = { rawFile ->
                    viewModel.onPhotoCaptured(rawFile) {
                        val hasEditor = navController.previousBackStackEntry?.destination?.route == Routes.EDITOR
                        if (hasEditor) {
                            navController.popBackStack()
                        } else {
                            navController.navigate(Routes.EDITOR) {
                                popUpTo(Routes.CAMERA) { inclusive = true }
                            }
                        }
                    }
                },
                onImportPhotos = { uris ->
                    viewModel.importPhotosFromUris(uris) {
                        val hasEditor = navController.previousBackStackEntry?.destination?.route == Routes.EDITOR
                        if (hasEditor) {
                            navController.popBackStack()
                        } else {
                            navController.navigate(Routes.EDITOR) {
                                popUpTo(Routes.CAMERA) { inclusive = true }
                            }
                        }
                    }
                },
                onOpenReview = {
                    val hasEditor = navController.previousBackStackEntry?.destination?.route == Routes.EDITOR
                    if (hasEditor) {
                        navController.popBackStack()
                    } else {
                        navController.navigate(Routes.EDITOR) {
                            popUpTo(Routes.CAMERA) { inclusive = true }
                        }
                    }
                },
                onBack = { navController.popBackStack() }
            )
        }

        composable(Routes.EDITOR) {
            val defaultTitle = remember(uiState.language) {
                viewModel.defaultTitleForNow(uiState.language)
            }
            ScanEditorScreen(
                uiState = uiState,
                defaultTitle = defaultTitle,
                onSelectPage = viewModel::selectDraftPage,
                onRemovePage = viewModel::removeDraftPage,
                onUpdatePage = viewModel::updateActiveDraftPage,
                onAddPageViaCamera = {
                    navController.navigate(Routes.CAMERA)
                },
                onAddPagesViaGallery = { uris ->
                    viewModel.importPhotosFromUris(uris) {}
                },
                onSaveDocument = { title ->
                    val wasAppendId = uiState.appendToDocId
                    viewModel.saveDraftSession(title) { savedDoc ->
                        if (wasAppendId != null) {
                            navController.popBackStack(Routes.detail(savedDoc.id), inclusive = false)
                        } else {
                            navController.navigate(Routes.detail(savedDoc.id)) {
                                popUpTo(Routes.HOME) { inclusive = false }
                            }
                        }
                    }
                },
                onBack = { navController.popBackStack() }
            )
        }

        composable(
            route = Routes.DETAIL,
            arguments = listOf(navArgument("docId") { type = NavType.StringType })
        ) { backStackEntry ->
            val docId = backStackEntry.arguments?.getString("docId") ?: ""
            val doc = uiState.documents.firstOrNull { it.id == docId }
            DocumentDetailScreen(
                document = doc,
                language = uiState.language,
                snackbarMessage = uiState.snackbarMessage,
                onClearSnackbar = viewModel::clearSnackbar,
                onBack = { navController.popBackStack() },
                onSharePdf = { d ->
                    context.startActivity(viewModel.createSharePdfIntent(d))
                },
                onShareImages = { d ->
                    context.startActivity(viewModel.createShareImagesIntent(d))
                },
                onSaveDownloads = { d ->
                    viewModel.savePdfToDownloads(d) {
                        launchSafExport(d)
                    }
                },
                onSaveGallery = { d ->
                    viewModel.savePagesToGallery(d) {
                        launchSafExport(d)
                    }
                },
                onSaveAsSaf = { d ->
                    launchSafExport(d)
                },
                onAddPageCamera = { d ->
                    viewModel.startAppendSession(d.id)
                    navController.navigate(Routes.CAMERA)
                },
                onAddPagesGallery = { d, uris ->
                    viewModel.startAppendSession(d.id)
                    viewModel.importPhotosFromUris(uris) {
                        navController.navigate(Routes.EDITOR)
                    }
                },
                onMovePage = { d, from, to ->
                    viewModel.movePageInDocument(d.id, from, to)
                },
                onDeletePage = { d, idx ->
                    viewModel.deletePageFromDocument(d.id, idx) {
                        navController.popBackStack()
                    }
                },
                onRenameDocument = { d, newTitle ->
                    viewModel.renameDocument(d.id, newTitle)
                },
                onDeleteDocument = { d ->
                    viewModel.deleteDocument(d.id) {
                        navController.popBackStack()
                    }
                }
            )
        }
    }
}
