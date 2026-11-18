package com.example.ui.screens

import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.R
import com.example.data.AppLanguage
import com.example.data.ScanFilter
import com.example.data.ScannedDocument
import com.example.ui.PaperScanUiState
import com.example.ui.formatByteSize
import com.example.ui.tr
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    uiState: PaperScanUiState,
    onSearchQueryChange: (String) -> Unit,
    onToggleLanguage: () -> Unit,
    onToggleAutoSaveDownloads: (Boolean) -> Unit,
    onStartCameraScan: () -> Unit,
    onImportPhotos: (List<android.net.Uri>) -> Unit,
    onOpenDocument: (ScannedDocument) -> Unit,
    onSharePdf: (ScannedDocument) -> Unit,
    onShareImages: (ScannedDocument) -> Unit,
    onSaveToDownloads: (ScannedDocument) -> Unit,
    onSaveToGallery: (ScannedDocument) -> Unit,
    onRenameDocument: (ScannedDocument, String) -> Unit,
    onDeleteDocument: (ScannedDocument) -> Unit,
    onClearSnackbar: () -> Unit
) {
    val lang = uiState.language
    val snackbarHostState = remember { SnackbarHostState() }
    var shareSheetDoc by remember { mutableStateOf<ScannedDocument?>(null) }
    var renameDoc by remember { mutableStateOf<ScannedDocument?>(null) }
    var deleteDoc by remember { mutableStateOf<ScannedDocument?>(null) }

    LaunchedEffect(uiState.snackbarMessage) {
        val msg = uiState.snackbarMessage
        if (!msg.isNullOrBlank()) {
            snackbarHostState.showSnackbar(msg)
            onClearSnackbar()
        }
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(10)
    ) { uris ->
        if (uris.isNotEmpty()) {
            onImportPhotos(uris)
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.navigationBarsPadding()
            ) {
                FloatingActionButton(
                    onClick = {
                        photoPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.testTag("fab_import_gallery")
                ) {
                    Icon(
                        imageVector = Icons.Filled.PhotoLibrary,
                        contentDescription = tr(lang, R.string.action_import_gallery_si, R.string.action_import_gallery_en)
                    )
                }

                ExtendedFloatingActionButton(
                    onClick = onStartCameraScan,
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    icon = {
                        Icon(
                            imageVector = Icons.Filled.CameraAlt,
                            contentDescription = null
                        )
                    },
                    text = {
                        Text(
                            text = tr(lang, R.string.action_scan_paper_si, R.string.action_scan_paper_en),
                            fontWeight = FontWeight.SemiBold
                        )
                    },
                    modifier = Modifier.testTag("fab_scan_paper")
                )
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Distinct Editorial Header
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 1.dp,
                shadowElevation = 2.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                                    MaterialTheme.colorScheme.surface
                                )
                            )
                        )
                        .padding(top = 44.dp, start = 20.dp, end = 20.dp, bottom = 14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(44.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Filled.Description,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimary,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = stringResourceCompat(),
                                    style = MaterialTheme.typography.headlineSmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = tr(lang, R.string.home_subtitle_si, R.string.home_subtitle_en),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(50),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .clip(RoundedCornerShape(50))
                                .clickable(onClick = onToggleLanguage)
                                .testTag("language_toggle_button")
                        ) {
                            Text(
                                text = tr(lang, R.string.toggle_lang_si, R.string.toggle_lang_en),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Offline & Zero-DB Status + Downloads Toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = MaterialTheme.colorScheme.secondaryContainer
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.CloudOff,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = tr(lang, R.string.badge_offline_si, R.string.badge_offline_en),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                        }

                        if (uiState.documents.isNotEmpty()) {
                            val totalBytes = remember(uiState.documents) {
                                uiState.documents.sumOf { it.totalSizeBytes }
                            }
                            Text(
                                text = tr(
                                    lang,
                                    R.string.stats_summary_si,
                                    R.string.stats_summary_en,
                                    uiState.documents.size,
                                    formatByteSize(totalBytes)
                                ),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
                                .padding(horizontal = 12.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Folder,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = tr(
                                        lang,
                                        R.string.auto_save_downloads_si,
                                        R.string.auto_save_downloads_en
                                    ),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            Switch(
                                checked = uiState.autoSaveDownloads,
                                onCheckedChange = onToggleAutoSaveDownloads,
                                modifier = Modifier.testTag("auto_save_downloads_switch")
                            )
                        }
                    }

                    if (uiState.documents.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(10.dp))
                        OutlinedTextField(
                            value = uiState.searchQuery,
                            onValueChange = onSearchQueryChange,
                            placeholder = {
                                Text(
                                    text = tr(lang, R.string.search_hint_si, R.string.search_hint_en),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Filled.Search,
                                    contentDescription = null
                                )
                            },
                            trailingIcon = {
                                if (uiState.searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { onSearchQueryChange("") }) {
                                        Icon(
                                            imageVector = Icons.Outlined.Close,
                                            contentDescription = tr(lang, R.string.cancel_si, R.string.cancel_en)
                                        )
                                    }
                                }
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                unfocusedContainerColor = MaterialTheme.colorScheme.background,
                                focusedContainerColor = MaterialTheme.colorScheme.background
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("search_documents_input")
                        )
                    }
                }
            }

            if (uiState.documents.isEmpty()) {
                EmptyDocumentsView(
                    language = lang,
                    onStartCameraScan = onStartCameraScan,
                    onImportPhotos = {
                        photoPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    }
                )
            } else {
                val docs = uiState.filteredDocuments
                BoxWithConstraints(
                    modifier = Modifier.fillMaxSize()
                ) {
                    val isWide = maxWidth >= 600.dp
                    if (isWide) {
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(minSize = 290.dp),
                            contentPadding = PaddingValues(
                                start = 16.dp,
                                end = 16.dp,
                                top = 16.dp,
                                bottom = 104.dp
                            ),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(docs, key = { it.id }) { doc ->
                                DocumentCard(
                                    doc = doc,
                                    language = lang,
                                    onClick = { onOpenDocument(doc) },
                                    onQuickShare = { shareSheetDoc = doc },
                                    onSaveDownloads = { onSaveToDownloads(doc) },
                                    onRename = { renameDoc = doc },
                                    onDelete = { deleteDoc = doc }
                                )
                            }
                        }
                    } else {
                        LazyColumn(
                            contentPadding = PaddingValues(
                                start = 16.dp,
                                end = 16.dp,
                                top = 14.dp,
                                bottom = 104.dp
                            ),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(docs, key = { it.id }) { doc ->
                                DocumentCard(
                                    doc = doc,
                                    language = lang,
                                    onClick = { onOpenDocument(doc) },
                                    onQuickShare = { shareSheetDoc = doc },
                                    onSaveDownloads = { onSaveToDownloads(doc) },
                                    onRename = { renameDoc = doc },
                                    onDelete = { deleteDoc = doc }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Share & Save Options Bottom Sheet
    shareSheetDoc?.let { doc ->
        ModalBottomSheet(
            onDismissRequest = { shareSheetDoc = null },
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = doc.title,
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))

                ShareOptionRow(
                    icon = Icons.Filled.PictureAsPdf,
                    title = tr(lang, R.string.share_pdf_si, R.string.share_pdf_en),
                    tint = MaterialTheme.colorScheme.primary,
                    testTag = "sheet_share_pdf_button",
                    onClick = {
                        shareSheetDoc = null
                        onSharePdf(doc)
                    }
                )
                ShareOptionRow(
                    icon = Icons.Filled.Image,
                    title = tr(lang, R.string.share_images_si, R.string.share_images_en),
                    tint = MaterialTheme.colorScheme.secondary,
                    testTag = "sheet_share_images_button",
                    onClick = {
                        shareSheetDoc = null
                        onShareImages(doc)
                    }
                )
                ShareOptionRow(
                    icon = Icons.Filled.Download,
                    title = tr(lang, R.string.save_downloads_si, R.string.save_downloads_en),
                    tint = MaterialTheme.colorScheme.tertiary,
                    testTag = "sheet_save_downloads_button",
                    onClick = {
                        shareSheetDoc = null
                        onSaveToDownloads(doc)
                    }
                )
                ShareOptionRow(
                    icon = Icons.Filled.PhotoLibrary,
                    title = tr(lang, R.string.save_gallery_si, R.string.save_gallery_en),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    testTag = "sheet_save_gallery_button",
                    onClick = {
                        shareSheetDoc = null
                        onSaveToGallery(doc)
                    }
                )
                Spacer(modifier = Modifier.height(12.dp))
            }
        }
    }

    // Rename Dialog
    renameDoc?.let { doc ->
        var titleInput by remember(doc.id) { mutableStateOf(doc.title) }
        AlertDialog(
            onDismissRequest = { renameDoc = null },
            title = { Text(tr(lang, R.string.rename_si, R.string.rename_en)) },
            text = {
                OutlinedTextField(
                    value = titleInput,
                    onValueChange = { titleInput = it },
                    label = { Text(tr(lang, R.string.doc_name_label_si, R.string.doc_name_label_en)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onRenameDocument(doc, titleInput)
                        renameDoc = null
                    }
                ) {
                    Text(tr(lang, R.string.confirm_si, R.string.confirm_en))
                }
            },
            dismissButton = {
                TextButton(onClick = { renameDoc = null }) {
                    Text(tr(lang, R.string.cancel_si, R.string.cancel_en))
                }
            }
        )
    }

    // Delete Confirmation Dialog
    deleteDoc?.let { doc ->
        AlertDialog(
            onDismissRequest = { deleteDoc = null },
            title = { Text(tr(lang, R.string.delete_si, R.string.delete_en)) },
            text = { Text(tr(lang, R.string.delete_confirm_si, R.string.delete_confirm_en)) },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteDocument(doc)
                        deleteDoc = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(tr(lang, R.string.delete_si, R.string.delete_en))
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteDoc = null }) {
                    Text(tr(lang, R.string.cancel_si, R.string.cancel_en))
                }
            }
        )
    }
}

@Composable
private fun stringResourceCompat(): String {
    val context = LocalContext.current
    return context.getString(R.string.app_name)
}

@Composable
private fun EmptyDocumentsView(
    language: AppLanguage,
    onStartCameraScan: () -> Unit,
    onImportPhotos: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 460.dp)
                .fillMaxWidth()
                .padding(bottom = 72.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Card(
                shape = RoundedCornerShape(24.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
                modifier = Modifier
                    .fillMaxWidth(0.84f)
                    .aspectRatio(4f / 3f)
            ) {
                Image(
                    painter = painterResource(id = R.drawable.img_empty_scanner),
                    contentDescription = tr(language, R.string.empty_title_si, R.string.empty_title_en),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = tr(language, R.string.empty_title_si, R.string.empty_title_en),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = tr(language, R.string.empty_desc_si, R.string.empty_desc_en),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = onStartCameraScan,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("empty_scan_button")
            ) {
                Icon(imageVector = Icons.Filled.CameraAlt, contentDescription = null)
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = tr(language, R.string.action_scan_paper_si, R.string.action_scan_paper_en),
                    style = MaterialTheme.typography.labelLarge
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            OutlinedButton(
                onClick = onImportPhotos,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("empty_import_button")
            ) {
                Icon(imageVector = Icons.Filled.PhotoLibrary, contentDescription = null)
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = tr(language, R.string.action_import_gallery_si, R.string.action_import_gallery_en),
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }
}

@Composable
private fun DocumentCard(
    doc: ScannedDocument,
    language: AppLanguage,
    onClick: () -> Unit,
    onQuickShare: () -> Unit,
    onSaveDownloads: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val dateText = remember(doc.updatedAtMillis) {
        SimpleDateFormat("MMM dd, yyyy • HH:mm", Locale.getDefault()).format(Date(doc.updatedAtMillis))
    }

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .testTag("doc_card_${doc.id}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(width = 68.dp, height = 88.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                AsyncImage(
                    model = File(doc.pagePaths.first()),
                    contentDescription = doc.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = Color.Black.copy(alpha = 0.72f),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(4.dp)
                ) {
                    Text(
                        text = "${doc.pagePaths.size}p",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = doc.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = dateText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val pageLabel = if (doc.pagePaths.size == 1) {
                        tr(language, R.string.page_singular_si, R.string.page_singular_en)
                    } else {
                        tr(language, R.string.pages_count_si, R.string.pages_count_en, doc.pagePaths.size)
                    }
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Text(
                            text = pageLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                    Text(
                        text = formatByteSize(doc.totalSizeBytes),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            IconButton(
                onClick = onQuickShare,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("share_doc_button_${doc.id}")
            ) {
                Icon(
                    imageVector = Icons.Filled.Share,
                    contentDescription = tr(language, R.string.detail_share_si, R.string.detail_share_en),
                    tint = MaterialTheme.colorScheme.primary
                )
            }

            Box {
                IconButton(
                    onClick = { menuExpanded = true },
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("more_doc_button_${doc.id}")
                ) {
                    Icon(
                        imageVector = Icons.Filled.MoreVert,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text(tr(language, R.string.detail_share_si, R.string.detail_share_en)) },
                        leadingIcon = { Icon(Icons.Filled.Share, contentDescription = null) },
                        onClick = {
                            menuExpanded = false
                            onQuickShare()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(tr(language, R.string.save_downloads_si, R.string.save_downloads_en)) },
                        leadingIcon = { Icon(Icons.Filled.Download, contentDescription = null) },
                        onClick = {
                            menuExpanded = false
                            onSaveDownloads()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(tr(language, R.string.rename_si, R.string.rename_en)) },
                        leadingIcon = { Icon(Icons.Filled.Edit, contentDescription = null) },
                        onClick = {
                            menuExpanded = false
                            onRename()
                        }
                    )
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = tr(language, R.string.delete_si, R.string.delete_en),
                                color = MaterialTheme.colorScheme.error
                            )
                        },
                        leadingIcon = {
                            Icon(
                                Icons.Filled.DeleteOutline,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error
                            )
                        },
                        onClick = {
                            menuExpanded = false
                            onDelete()
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun ShareOptionRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    tint: Color,
    testTag: String,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .testTag(testTag)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(tint.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = tint,
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
