package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.R
import com.example.data.ScannedDocument
import com.example.ui.formatByteSize
import com.example.ui.tr
import com.example.data.AppLanguage
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentDetailScreen(
    document: ScannedDocument?,
    language: AppLanguage,
    snackbarMessage: String?,
    onClearSnackbar: () -> Unit,
    onBack: () -> Unit,
    onSharePdf: (ScannedDocument) -> Unit,
    onShareImages: (ScannedDocument) -> Unit,
    onSaveDownloads: (ScannedDocument) -> Unit,
    onSaveGallery: (ScannedDocument) -> Unit,
    onSaveAsSaf: (ScannedDocument) -> Unit,
    onAddPageCamera: (ScannedDocument) -> Unit,
    onAddPagesGallery: (ScannedDocument, List<Uri>) -> Unit,
    onMovePage: (ScannedDocument, Int, Int) -> Unit,
    onDeletePage: (ScannedDocument, Int) -> Unit,
    onRenameDocument: (ScannedDocument, String) -> Unit,
    onDeleteDocument: (ScannedDocument) -> Unit
) {
    val snackbarHostState = remember { SnackbarHostState() }
    var showMoreMenu by remember { mutableStateOf(false) }
    var showShareSheet by remember { mutableStateOf(false) }
    var showAddSourceDialog by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    LaunchedEffect(snackbarMessage) {
        if (!snackbarMessage.isNullOrBlank()) {
            snackbarHostState.showSnackbar(snackbarMessage)
            onClearSnackbar()
        }
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(10)
    ) { uris ->
        if (uris.isNotEmpty() && document != null) {
            onAddPagesGallery(document, uris)
        }
    }

    if (document == null) {
        LaunchedEffect(Unit) { onBack() }
        return
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("detail_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = tr(language, R.string.back_cd_si, R.string.back_cd_en)
                        )
                    }
                },
                title = {
                    Column {
                        Text(
                            text = document.title,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        val pageLabel = if (document.pagePaths.size == 1) {
                            tr(language, R.string.page_singular_si, R.string.page_singular_en)
                        } else {
                            tr(language, R.string.pages_count_si, R.string.pages_count_en, document.pagePaths.size)
                        }
                        Text(
                            text = "$pageLabel • ${formatByteSize(document.totalSizeBytes)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { showMoreMenu = true },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("detail_more_button")
                    ) {
                        Icon(imageVector = Icons.Filled.MoreVert, contentDescription = null)
                    }
                    DropdownMenu(
                        expanded = showMoreMenu,
                        onDismissRequest = { showMoreMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text(tr(language, R.string.rename_si, R.string.rename_en)) },
                            leadingIcon = { Icon(Icons.Filled.Edit, contentDescription = null) },
                            onClick = {
                                showMoreMenu = false
                                showRenameDialog = true
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(tr(language, R.string.save_downloads_si, R.string.save_downloads_en)) },
                            leadingIcon = { Icon(Icons.Filled.Download, contentDescription = null) },
                            onClick = {
                                showMoreMenu = false
                                onSaveDownloads(document)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(tr(language, R.string.save_gallery_si, R.string.save_gallery_en)) },
                            leadingIcon = { Icon(Icons.Filled.PhotoLibrary, contentDescription = null) },
                            onClick = {
                                showMoreMenu = false
                                onSaveGallery(document)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(tr(language, R.string.save_as_si, R.string.save_as_en)) },
                            leadingIcon = { Icon(Icons.Filled.FolderOpen, contentDescription = null) },
                            onClick = {
                                showMoreMenu = false
                                onSaveAsSaf(document)
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
                                showMoreMenu = false
                                showDeleteDialog = true
                            }
                        )
                    }
                }
            )
        },
        bottomBar = {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 8.dp,
                tonalElevation = 3.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = { showAddSourceDialog = true },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp)
                            .testTag("detail_add_page_button")
                    ) {
                        Icon(imageVector = Icons.Filled.Add, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = tr(language, R.string.add_another_page_si, R.string.add_another_page_en),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Button(
                        onClick = { showShareSheet = true },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .weight(1.3f)
                            .height(50.dp)
                            .testTag("detail_share_button")
                    ) {
                        Icon(imageVector = Icons.Filled.Share, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = tr(language, R.string.detail_share_si, R.string.detail_share_en),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier
                    .widthIn(max = 680.dp)
                    .fillMaxSize()
            ) {
                itemsIndexed(document.pagePaths, key = { idx, path -> "${idx}_$path" }) { index, path ->
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            AsyncImage(
                                model = File(path),
                                contentDescription = "Page ${index + 1}",
                                contentScale = ContentScale.FillWidth,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(0.72f)
                                    .background(Color.White)
                            )
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${index + 1} / ${document.pagePaths.size}",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Row {
                                    if (index > 0) {
                                        IconButton(
                                            onClick = { onMovePage(document, index, index - 1) },
                                            modifier = Modifier.minimumInteractiveComponentSize()
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.ArrowUpward,
                                                contentDescription = "Move page up"
                                            )
                                        }
                                    }
                                    if (index < document.pagePaths.lastIndex) {
                                        IconButton(
                                            onClick = { onMovePage(document, index, index + 1) },
                                            modifier = Modifier.minimumInteractiveComponentSize()
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.ArrowDownward,
                                                contentDescription = "Move page down"
                                            )
                                        }
                                    }
                                    IconButton(
                                        onClick = { onDeletePage(document, index) },
                                        modifier = Modifier.minimumInteractiveComponentSize()
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.DeleteOutline,
                                            contentDescription = tr(language, R.string.delete_si, R.string.delete_en),
                                            tint = MaterialTheme.colorScheme.error
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showShareSheet) {
        ModalBottomSheet(
            onDismissRequest = { showShareSheet = false },
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
                    text = tr(language, R.string.detail_share_si, R.string.detail_share_en),
                    style = MaterialTheme.typography.titleLarge
                )
                DetailSheetRow(
                    icon = Icons.Filled.PictureAsPdf,
                    label = tr(language, R.string.share_pdf_si, R.string.share_pdf_en),
                    tint = MaterialTheme.colorScheme.primary,
                    testTag = "detail_share_pdf_button",
                    onClick = {
                        showShareSheet = false
                        onSharePdf(document)
                    }
                )
                DetailSheetRow(
                    icon = Icons.Filled.Image,
                    label = tr(language, R.string.share_images_si, R.string.share_images_en),
                    tint = MaterialTheme.colorScheme.secondary,
                    testTag = "detail_share_images_button",
                    onClick = {
                        showShareSheet = false
                        onShareImages(document)
                    }
                )
                DetailSheetRow(
                    icon = Icons.Filled.Download,
                    label = tr(language, R.string.save_downloads_si, R.string.save_downloads_en),
                    tint = MaterialTheme.colorScheme.tertiary,
                    testTag = "detail_save_downloads_button",
                    onClick = {
                        showShareSheet = false
                        onSaveDownloads(document)
                    }
                )
                DetailSheetRow(
                    icon = Icons.Filled.PhotoLibrary,
                    label = tr(language, R.string.save_gallery_si, R.string.save_gallery_en),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    testTag = "detail_save_gallery_button",
                    onClick = {
                        showShareSheet = false
                        onSaveGallery(document)
                    }
                )
                DetailSheetRow(
                    icon = Icons.Filled.FolderOpen,
                    label = tr(language, R.string.save_as_si, R.string.save_as_en),
                    tint = MaterialTheme.colorScheme.primary,
                    testTag = "detail_save_saf_button",
                    onClick = {
                        showShareSheet = false
                        onSaveAsSaf(document)
                    }
                )
                Spacer(modifier = Modifier.height(12.dp))
            }
        }
    }

    if (showAddSourceDialog) {
        AlertDialog(
            onDismissRequest = { showAddSourceDialog = false },
            title = { Text(tr(language, R.string.add_another_page_si, R.string.add_another_page_en)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = {
                            showAddSourceDialog = false
                            onAddPageCamera(document)
                        },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Filled.CameraAlt, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(tr(language, R.string.action_scan_paper_si, R.string.action_scan_paper_en))
                    }
                    OutlinedButton(
                        onClick = {
                            showAddSourceDialog = false
                            photoPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Filled.PhotoLibrary, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(tr(language, R.string.action_import_gallery_si, R.string.action_import_gallery_en))
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showAddSourceDialog = false }) {
                    Text(tr(language, R.string.cancel_si, R.string.cancel_en))
                }
            }
        )
    }

    if (showRenameDialog) {
        var titleInput by remember(document.id) { mutableStateOf(document.title) }
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = { Text(tr(language, R.string.rename_si, R.string.rename_en)) },
            text = {
                OutlinedTextField(
                    value = titleInput,
                    onValueChange = { titleInput = it },
                    label = { Text(tr(language, R.string.doc_name_label_si, R.string.doc_name_label_en)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onRenameDocument(document, titleInput)
                        showRenameDialog = false
                    }
                ) {
                    Text(tr(language, R.string.confirm_si, R.string.confirm_en))
                }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = false }) {
                    Text(tr(language, R.string.cancel_si, R.string.cancel_en))
                }
            }
        )
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(tr(language, R.string.delete_si, R.string.delete_en)) },
            text = { Text(tr(language, R.string.delete_confirm_si, R.string.delete_confirm_en)) },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteDialog = false
                        onDeleteDocument(document)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(tr(language, R.string.delete_si, R.string.delete_en))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text(tr(language, R.string.cancel_si, R.string.cancel_en))
                }
            }
        )
    }
}

@Composable
private fun DetailSheetRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
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
                text = label,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
