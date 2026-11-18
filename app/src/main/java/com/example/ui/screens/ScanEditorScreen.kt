package com.example.ui.screens

import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.CropFree
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.R
import com.example.data.AppLanguage
import com.example.data.DraftPage
import com.example.data.ImageProcessor
import com.example.data.NormalizedPoint
import com.example.data.QuadCorners
import com.example.data.ScanFilter
import com.example.ui.PaperScanUiState
import com.example.ui.tr
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.hypot
import kotlin.math.min

@Composable
fun ScanEditorScreen(
    uiState: PaperScanUiState,
    defaultTitle: String,
    onSelectPage: (Int) -> Unit,
    onRemovePage: (Int) -> Unit,
    onUpdatePage: (QuadCorners?, ScanFilter?, Boolean) -> Unit,
    onAddPageViaCamera: () -> Unit,
    onAddPagesViaGallery: (List<Uri>) -> Unit,
    onSaveDocument: (String) -> Unit,
    onBack: () -> Unit
) {
    val lang = uiState.language
    val activePage = uiState.activeDraftPage
    val isAppending = uiState.appendToDocId != null

    var isCropMode by remember { mutableStateOf(false) }
    var docTitle by remember(defaultTitle) { mutableStateOf(defaultTitle) }
    var showAddSourceDialog by remember { mutableStateOf(false) }

    // Local mutable corners while dragging in Crop mode
    var editingCorners by remember(activePage?.id, activePage?.corners) {
        mutableStateOf(activePage?.corners ?: QuadCorners.DEFAULT)
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(10)
    ) { uris ->
        if (uris.isNotEmpty()) {
            onAddPagesViaGallery(uris)
        }
    }

    LaunchedEffect(uiState.draftPages.size) {
        if (uiState.draftPages.isEmpty() && !uiState.isProcessing) {
            onBack()
        }
    }

    Scaffold(
        containerColor = Color(0xFF0E1417),
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Top Studio Bar
            Surface(
                color = Color(0xFF161E23),
                tonalElevation = 2.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = onBack,
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("editor_back_button")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = tr(lang, R.string.back_cd_si, R.string.back_cd_en),
                                    tint = Color.White
                                )
                            }
                            Text(
                                text = tr(lang, R.string.editor_title_si, R.string.editor_title_en),
                                style = MaterialTheme.typography.titleLarge,
                                color = Color.White
                            )
                        }

                        // Mode Switcher: Crop vs Enhance
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = {
                                    onUpdatePage(
                                        if (isCropMode) editingCorners else null,
                                        null,
                                        true
                                    )
                                },
                                enabled = !uiState.isProcessing && activePage != null,
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("editor_rotate_button")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.RotateRight,
                                    contentDescription = tr(lang, R.string.rotate_si, R.string.rotate_en),
                                    tint = Color.White
                                )
                            }

                            Surface(
                                shape = RoundedCornerShape(50),
                                color = if (isCropMode) Color(0xFF14B8A6) else Color(0xFF233038),
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .clip(RoundedCornerShape(50))
                                    .clickable(enabled = activePage != null) {
                                        if (isCropMode) {
                                            onUpdatePage(editingCorners, null, false)
                                            isCropMode = false
                                        } else {
                                            editingCorners = activePage?.corners ?: QuadCorners.DEFAULT
                                            isCropMode = true
                                        }
                                    }
                                    .testTag("editor_crop_toggle_button")
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                                ) {
                                    Icon(
                                        imageVector = if (isCropMode) Icons.Filled.Check else Icons.Filled.Crop,
                                        contentDescription = null,
                                        tint = if (isCropMode) Color(0xFF042F2E) else Color.White,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = if (isCropMode) {
                                            tr(lang, R.string.apply_crop_si, R.string.apply_crop_en)
                                        } else {
                                            tr(lang, R.string.edit_corners_si, R.string.edit_corners_en)
                                        },
                                        style = MaterialTheme.typography.labelLarge,
                                        color = if (isCropMode) Color(0xFF042F2E) else Color.White
                                    )
                                }
                            }
                        }
                    }

                    // Multi-page thumbnail strip when more than 1 page
                    if (uiState.draftPages.size > 1) {
                        Spacer(modifier = Modifier.height(6.dp))
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = PaddingValues(horizontal = 4.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            itemsIndexed(uiState.draftPages, key = { _, p -> p.id }) { idx, page ->
                                val selected = idx == uiState.activeDraftIndex
                                Box(
                                    modifier = Modifier
                                        .size(width = 52.dp, height = 66.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .border(
                                            width = if (selected) 2.5.dp else 1.dp,
                                            color = if (selected) Color(0xFF2DD4BF) else Color.White.copy(alpha = 0.25f),
                                            shape = RoundedCornerShape(8.dp)
                                        )
                                        .clickable { onSelectPage(idx) }
                                ) {
                                    AsyncImage(
                                        model = File(page.processedFilePath),
                                        contentDescription = "Page ${idx + 1}",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                    Surface(
                                        shape = CircleShape,
                                        color = Color.Black.copy(alpha = 0.7f),
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .padding(2.dp)
                                            .size(18.dp)
                                            .clickable { onRemovePage(idx) }
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Close,
                                            contentDescription = "Remove page ${idx + 1}",
                                            tint = Color.White,
                                            modifier = Modifier.padding(2.dp)
                                        )
                                    }
                                    Surface(
                                        color = Color.Black.copy(alpha = 0.7f),
                                        shape = RoundedCornerShape(4.dp),
                                        modifier = Modifier
                                            .align(Alignment.BottomStart)
                                            .padding(2.dp)
                                    ) {
                                        Text(
                                            text = "${idx + 1}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color.White,
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Main Canvas Stage
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                if (activePage != null) {
                    if (isCropMode) {
                        PerspectiveCropStage(
                            page = activePage,
                            corners = editingCorners,
                            onCornersChange = { editingCorners = it },
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        AsyncImage(
                            model = File(activePage.processedFilePath),
                            contentDescription = tr(lang, R.string.editor_title_si, R.string.editor_title_en),
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(12.dp))
                        )
                    }
                }

                if (uiState.isProcessing) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = Color.Black.copy(alpha = 0.72f)
                    ) {
                        CircularProgressIndicator(
                            color = Color(0xFF2DD4BF),
                            modifier = Modifier.padding(20.dp)
                        )
                    }
                }
            }

            // Bottom Studio Controls
            Surface(
                color = Color(0xFF161E23),
                tonalElevation = 4.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (isCropMode) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = tr(lang, R.string.editor_hint_si, R.string.editor_hint_en),
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF9DB0BA),
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(
                                onClick = { editingCorners = QuadCorners.FULL },
                                modifier = Modifier.testTag("editor_reset_corners_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.CropFree,
                                    contentDescription = null,
                                    tint = Color(0xFF2DD4BF),
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = tr(lang, R.string.reset_corners_si, R.string.reset_corners_en),
                                    color = Color(0xFF2DD4BF)
                                )
                            }
                        }

                        Button(
                            onClick = {
                                onUpdatePage(editingCorners, null, false)
                                isCropMode = false
                            },
                            enabled = !uiState.isProcessing,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .testTag("editor_apply_crop_button")
                        ) {
                            Icon(imageVector = Icons.Filled.Check, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(tr(lang, R.string.apply_crop_si, R.string.apply_crop_en))
                        }
                    } else {
                        // Filter Selector Row
                        val currentFilter = activePage?.filter ?: ScanFilter.MAGIC_COLOR
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilterChipItem(
                                label = tr(lang, R.string.filter_magic_si, R.string.filter_magic_en),
                                selected = currentFilter == ScanFilter.MAGIC_COLOR,
                                onClick = { onUpdatePage(null, ScanFilter.MAGIC_COLOR, false) },
                                testTag = "filter_magic_color",
                                modifier = Modifier.weight(1f)
                            )
                            FilterChipItem(
                                label = tr(lang, R.string.filter_bw_si, R.string.filter_bw_en),
                                selected = currentFilter == ScanFilter.BLACK_WHITE,
                                onClick = { onUpdatePage(null, ScanFilter.BLACK_WHITE, false) },
                                testTag = "filter_black_white",
                                modifier = Modifier.weight(1f)
                            )
                            FilterChipItem(
                                label = tr(lang, R.string.filter_gray_si, R.string.filter_gray_en),
                                selected = currentFilter == ScanFilter.GRAYSCALE,
                                onClick = { onUpdatePage(null, ScanFilter.GRAYSCALE, false) },
                                testTag = "filter_grayscale",
                                modifier = Modifier.weight(1f)
                            )
                            FilterChipItem(
                                label = tr(lang, R.string.filter_original_si, R.string.filter_original_en),
                                selected = currentFilter == ScanFilter.ORIGINAL,
                                onClick = { onUpdatePage(null, ScanFilter.ORIGINAL, false) },
                                testTag = "filter_original",
                                modifier = Modifier.weight(1f)
                            )
                        }

                        if (!isAppending) {
                            OutlinedTextField(
                                value = docTitle,
                                onValueChange = { docTitle = it },
                                label = {
                                    Text(
                                        text = tr(lang, R.string.doc_name_label_si, R.string.doc_name_label_en),
                                        color = Color(0xFF9DB0BA)
                                    )
                                },
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White,
                                    focusedBorderColor = Color(0xFF2DD4BF),
                                    unfocusedBorderColor = Color(0xFF32414A)
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("editor_doc_title_input")
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedButton(
                                onClick = { showAddSourceDialog = true },
                                enabled = !uiState.isProcessing,
                                shape = RoundedCornerShape(14.dp),
                                border = BorderStroke(1.dp, Color(0xFF3A4D58)),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(50.dp)
                                    .testTag("editor_add_page_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Add,
                                    contentDescription = null,
                                    tint = Color.White
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = tr(lang, R.string.add_another_page_si, R.string.add_another_page_en),
                                    color = Color.White,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            Button(
                                onClick = { onSaveDocument(docTitle) },
                                enabled = !uiState.isProcessing && uiState.draftPages.isNotEmpty(),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .weight(1.3f)
                                    .height(50.dp)
                                    .testTag("editor_save_button")
                            ) {
                                Icon(imageVector = Icons.Filled.Save, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = tr(lang, R.string.save_doc_si, R.string.save_doc_en),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddSourceDialog) {
        AlertDialog(
            onDismissRequest = { showAddSourceDialog = false },
            title = { Text(tr(lang, R.string.add_another_page_si, R.string.add_another_page_en)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = {
                            showAddSourceDialog = false
                            onAddPageViaCamera()
                        },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Filled.CameraAlt, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(tr(lang, R.string.action_scan_paper_si, R.string.action_scan_paper_en))
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
                        Text(tr(lang, R.string.action_import_gallery_si, R.string.action_import_gallery_en))
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showAddSourceDialog = false }) {
                    Text(tr(lang, R.string.cancel_si, R.string.cancel_en))
                }
            }
        )
    }
}

@Composable
private fun FilterChipItem(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    testTag: String,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (selected) Color(0xFF14B8A6).copy(alpha = 0.22f) else Color(0xFF212C33),
        border = BorderStroke(
            width = if (selected) 1.5.dp else 1.dp,
            color = if (selected) Color(0xFF2DD4BF) else Color(0xFF32414A)
        ),
        modifier = modifier
            .minimumInteractiveComponentSize()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .testTag(testTag)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 10.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.Tune,
                contentDescription = null,
                tint = if (selected) Color(0xFF5EEAD4) else Color(0xFF9DB0BA),
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = if (selected) Color.White else Color(0xFFCBD5E1),
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun PerspectiveCropStage(
    page: DraftPage,
    corners: QuadCorners,
    onCornersChange: (QuadCorners) -> Unit,
    modifier: Modifier = Modifier
) {
    val bitmap by produceState<Bitmap?>(initialValue = null, page.rawFilePath, page.rotationDegrees) {
        value = withContext(Dispatchers.Default) {
            val base = ImageProcessor.decodeSampledAndOriented(page.rawFilePath, maxDim = 1400)
                ?: return@withContext null
            val rotated = ImageProcessor.rotateBitmap(base, page.rotationDegrees)
            if (rotated !== base && !base.isRecycled) base.recycle()
            rotated
        }
    }

    val currentBitmap = bitmap
    if (currentBitmap == null) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = Color(0xFF2DD4BF))
        }
        return
    }

    val density = LocalDensity.current
    val touchThresholdPx = with(density) { 46.dp.toPx() }
    var activeHandleIndex by remember { mutableIntStateOf(-1) }
    var latestCorners by remember(corners) { mutableStateOf(corners) }

    BoxWithConstraints(modifier = modifier) {
        val boxW = constraints.maxWidth.toFloat().coerceAtLeast(1f)
        val boxH = constraints.maxHeight.toFloat().coerceAtLeast(1f)
        val bmpW = currentBitmap.width.toFloat().coerceAtLeast(1f)
        val bmpH = currentBitmap.height.toFloat().coerceAtLeast(1f)

        val scale = min(boxW / bmpW, boxH / bmpH)
        val dispW = bmpW * scale
        val dispH = bmpH * scale
        val left = (boxW - dispW) / 2f
        val top = (boxH - dispH) / 2f
        val imageRect = Rect(left, top, left + dispW, top + dispH)

        fun toScreen(p: NormalizedPoint): Offset = Offset(
            x = imageRect.left + p.x * imageRect.width,
            y = imageRect.top + p.y * imageRect.height
        )

        fun toNormalized(offset: Offset): NormalizedPoint = NormalizedPoint(
            x = ((offset.x - imageRect.left) / imageRect.width).coerceIn(0.0f, 1.0f),
            y = ((offset.y - imageRect.top) / imageRect.height).coerceIn(0.0f, 1.0f)
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(imageRect, page.id) {
                    detectDragGestures(
                        onDragStart = { startOffset ->
                            val pts = listOf(
                                toScreen(latestCorners.topLeft),
                                toScreen(latestCorners.topRight),
                                toScreen(latestCorners.bottomRight),
                                toScreen(latestCorners.bottomLeft)
                            )
                            var bestIdx = -1
                            var bestDist = touchThresholdPx
                            pts.forEachIndexed { idx, pt ->
                                val d = hypot(pt.x - startOffset.x, pt.y - startOffset.y)
                                if (d < bestDist) {
                                    bestDist = d
                                    bestIdx = idx
                                }
                            }
                            activeHandleIndex = bestIdx
                        },
                        onDragEnd = { activeHandleIndex = -1 },
                        onDragCancel = { activeHandleIndex = -1 },
                        onDrag = { change, dragAmount ->
                            if (activeHandleIndex == -1) return@detectDragGestures
                            change.consume()
                            val currentList = listOf(
                                latestCorners.topLeft,
                                latestCorners.topRight,
                                latestCorners.bottomRight,
                                latestCorners.bottomLeft
                            )
                            val curScreen = toScreen(currentList[activeHandleIndex])
                            val moved = toNormalized(curScreen + dragAmount)
                            val updated = when (activeHandleIndex) {
                                0 -> latestCorners.copy(topLeft = moved)
                                1 -> latestCorners.copy(topRight = moved)
                                2 -> latestCorners.copy(bottomRight = moved)
                                else -> latestCorners.copy(bottomLeft = moved)
                            }
                            latestCorners = updated
                            onCornersChange(updated)
                        }
                    )
                }
        ) {
            Image(
                bitmap = currentBitmap.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize()
            )

            Canvas(modifier = Modifier.fillMaxSize()) {
                val p0 = toScreen(latestCorners.topLeft)
                val p1 = toScreen(latestCorners.topRight)
                val p2 = toScreen(latestCorners.bottomRight)
                val p3 = toScreen(latestCorners.bottomLeft)

                // Dim outside selected quad
                val scrimPath = Path().apply {
                    fillType = PathFillType.EvenOdd
                    addRect(imageRect)
                    moveTo(p0.x, p0.y)
                    lineTo(p1.x, p1.y)
                    lineTo(p2.x, p2.y)
                    lineTo(p3.x, p3.y)
                    close()
                }
                drawPath(path = scrimPath, color = Color.Black.copy(alpha = 0.5f))

                // Quad outline
                val quadPath = Path().apply {
                    moveTo(p0.x, p0.y)
                    lineTo(p1.x, p1.y)
                    lineTo(p2.x, p2.y)
                    lineTo(p3.x, p3.y)
                    close()
                }
                drawPath(
                    path = quadPath,
                    color = Color(0xFF2DD4BF),
                    style = Stroke(width = 2.5.dp.toPx())
                )

                // Rule-of-thirds guide lines inside quad
                for (t in listOf(0.333f, 0.666f)) {
                    val topPt = Offset(p0.x + (p1.x - p0.x) * t, p0.y + (p1.y - p0.y) * t)
                    val botPt = Offset(p3.x + (p2.x - p3.x) * t, p3.y + (p2.y - p3.y) * t)
                    drawLine(Color.White.copy(alpha = 0.35f), topPt, botPt, strokeWidth = 1.dp.toPx())

                    val leftPt = Offset(p0.x + (p3.x - p0.x) * t, p0.y + (p3.y - p0.y) * t)
                    val rightPt = Offset(p1.x + (p2.x - p1.x) * t, p1.y + (p2.y - p1.y) * t)
                    drawLine(Color.White.copy(alpha = 0.35f), leftPt, rightPt, strokeWidth = 1.dp.toPx())
                }

                // Corner Handles
                listOf(p0, p1, p2, p3).forEachIndexed { idx, pt ->
                    val isDragging = idx == activeHandleIndex
                    drawCircle(
                        color = Color(0xFF2DD4BF).copy(alpha = if (isDragging) 0.45f else 0.25f),
                        radius = if (isDragging) 22.dp.toPx() else 16.dp.toPx(),
                        center = pt
                    )
                    drawCircle(
                        color = Color.White,
                        radius = 9.dp.toPx(),
                        center = pt
                    )
                    drawCircle(
                        color = Color(0xFF0C4A61),
                        radius = 5.dp.toPx(),
                        center = pt
                    )
                }
            }
        }
    }
}
