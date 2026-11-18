package com.example.ui

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.R
import com.example.data.AppLanguage
import com.example.data.DocumentFileRepository
import com.example.data.DraftPage
import com.example.data.ImageProcessor
import com.example.data.QuadCorners
import com.example.data.ScanFilter
import com.example.data.ScannedDocument
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

data class PaperScanUiState(
    val documents: List<ScannedDocument> = emptyList(),
    val searchQuery: String = "",
    val language: AppLanguage = AppLanguage.SINHALA,
    val autoSaveDownloads: Boolean = true,
    val draftPages: List<DraftPage> = emptyList(),
    val activeDraftIndex: Int = 0,
    val appendToDocId: String? = null,
    val isProcessing: Boolean = false,
    val snackbarMessage: String? = null
) {
    val filteredDocuments: List<ScannedDocument>
        get() {
            val q = searchQuery.trim()
            return if (q.isEmpty()) {
                documents
            } else {
                documents.filter { it.title.contains(q, ignoreCase = true) }
            }
        }

    val activeDraftPage: DraftPage?
        get() = draftPages.getOrNull(activeDraftIndex)
}

class PaperScanViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = DocumentFileRepository(application.applicationContext)

    private val _documents = MutableStateFlow<List<ScannedDocument>>(emptyList())
    private val _searchQuery = MutableStateFlow("")
    private val _draftPages = MutableStateFlow<List<DraftPage>>(emptyList())
    private val _activeDraftIndex = MutableStateFlow(0)
    private val _appendToDocId = MutableStateFlow<String?>(null)
    private val _isProcessing = MutableStateFlow(false)
    private val _snackbarMessage = MutableStateFlow<String?>(null)

    private val _language = repository.languageFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = AppLanguage.SINHALA
    )
    private val _autoSaveDownloads = repository.autoSaveDownloadsFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = true
    )

    val uiState: StateFlow<PaperScanUiState> = combine(
        combine(_documents, _searchQuery, _language, _autoSaveDownloads) { docs, query, lang, autoSave ->
            QuadTuple(docs, query, lang, autoSave)
        },
        combine(_draftPages, _activeDraftIndex, _appendToDocId, _isProcessing, _snackbarMessage) { drafts, idx, appendId, busy, msg ->
            DraftTuple(drafts, idx, appendId, busy, msg)
        }
    ) { main, draft ->
        PaperScanUiState(
            documents = main.docs,
            searchQuery = main.query,
            language = main.lang,
            autoSaveDownloads = main.autoSave,
            draftPages = draft.drafts,
            activeDraftIndex = draft.idx,
            appendToDocId = draft.appendId,
            isProcessing = draft.busy,
            snackbarMessage = draft.msg
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = PaperScanUiState()
    )

    init {
        refreshDocuments()
    }

    fun refreshDocuments() {
        viewModelScope.launch {
            _documents.value = repository.loadAllDocuments()
        }
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun toggleLanguage() {
        viewModelScope.launch {
            val next = if (_language.value == AppLanguage.SINHALA) {
                AppLanguage.ENGLISH
            } else {
                AppLanguage.SINHALA
            }
            repository.setLanguage(next)
        }
    }

    fun setAutoSaveDownloads(enabled: Boolean) {
        viewModelScope.launch {
            repository.setAutoSaveDownloads(enabled)
        }
    }

    fun clearSnackbar() {
        _snackbarMessage.value = null
    }

    fun startNewScanSession() {
        repository.clearDraftCache()
        _draftPages.value = emptyList()
        _activeDraftIndex.value = 0
        _appendToDocId.value = null
    }

    fun startAppendSession(docId: String) {
        repository.clearDraftCache()
        _draftPages.value = emptyList()
        _activeDraftIndex.value = 0
        _appendToDocId.value = docId
    }

    fun createCameraCaptureFile(): File {
        return repository.createTempDraftFile(prefix = "cam_raw", ext = "jpg")
    }

    fun onPhotoCaptured(rawFile: File, onReady: () -> Unit = {}) {
        viewModelScope.launch {
            _isProcessing.value = true
            try {
                val processedFile = repository.createTempDraftFile(prefix = "proc", ext = "jpg")
                val ok = withContext(Dispatchers.Default) {
                    ImageProcessor.processAndSavePage(
                        rawPath = rawFile.absolutePath,
                        outputPath = processedFile.absolutePath,
                        rotationDegrees = 0,
                        corners = QuadCorners.DEFAULT,
                        filter = ScanFilter.MAGIC_COLOR
                    )
                }
                if (ok) {
                    val newPage = DraftPage(
                        id = UUID.randomUUID().toString(),
                        rawFilePath = rawFile.absolutePath,
                        processedFilePath = processedFile.absolutePath,
                        rotationDegrees = 0,
                        corners = QuadCorners.DEFAULT,
                        filter = ScanFilter.MAGIC_COLOR
                    )
                    _draftPages.update { current -> current + newPage }
                    _activeDraftIndex.value = (_draftPages.value.size - 1).coerceAtLeast(0)
                    onReady()
                }
            } finally {
                _isProcessing.value = false
            }
        }
    }

    fun importPhotosFromUris(uris: List<Uri>, onReady: () -> Unit) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            _isProcessing.value = true
            try {
                val ctx = getApplication<Application>().applicationContext
                val added = withContext(Dispatchers.Default) {
                    uris.mapNotNull { uri ->
                        val rawFile = repository.createTempDraftFile(prefix = "pick_raw", ext = "jpg")
                        if (!ImageProcessor.copyUriToTempFile(ctx, uri, rawFile)) {
                            return@mapNotNull null
                        }
                        val processedFile = repository.createTempDraftFile(prefix = "proc", ext = "jpg")
                        val ok = ImageProcessor.processAndSavePage(
                            rawPath = rawFile.absolutePath,
                            outputPath = processedFile.absolutePath,
                            rotationDegrees = 0,
                            corners = QuadCorners.FULL,
                            filter = ScanFilter.MAGIC_COLOR
                        )
                        if (ok) {
                            DraftPage(
                                id = UUID.randomUUID().toString(),
                                rawFilePath = rawFile.absolutePath,
                                processedFilePath = processedFile.absolutePath,
                                rotationDegrees = 0,
                                corners = QuadCorners.FULL,
                                filter = ScanFilter.MAGIC_COLOR
                            )
                        } else null
                    }
                }
                if (added.isNotEmpty()) {
                    _draftPages.update { it + added }
                    _activeDraftIndex.value = (_draftPages.value.size - 1).coerceAtLeast(0)
                    onReady()
                }
            } finally {
                _isProcessing.value = false
            }
        }
    }

    fun selectDraftPage(index: Int) {
        if (index in _draftPages.value.indices) {
            _activeDraftIndex.value = index
        }
    }

    fun removeDraftPage(index: Int) {
        val current = _draftPages.value
        if (index !in current.indices) return
        val updated = current.filterIndexed { i, _ -> i != index }
        _draftPages.value = updated
        _activeDraftIndex.value = (_activeDraftIndex.value).coerceIn(0, (updated.size - 1).coerceAtLeast(0))
    }

    fun updateActiveDraftPage(
        newCorners: QuadCorners? = null,
        newFilter: ScanFilter? = null,
        rotateClockwise90: Boolean = false
    ) {
        val currentList = _draftPages.value
        val idx = _activeDraftIndex.value
        val page = currentList.getOrNull(idx) ?: return

        val updatedRotation = if (rotateClockwise90) (page.rotationDegrees + 90) % 360 else page.rotationDegrees
        val updatedCorners = when {
            newCorners != null -> newCorners
            rotateClockwise90 -> page.corners.rotateClockwise()
            else -> page.corners
        }
        val updatedFilter = newFilter ?: page.filter

        viewModelScope.launch {
            _isProcessing.value = true
            try {
                val nextOutFile = repository.createTempDraftFile(prefix = "proc", ext = "jpg")
                val ok = withContext(Dispatchers.Default) {
                    ImageProcessor.processAndSavePage(
                        rawPath = page.rawFilePath,
                        outputPath = nextOutFile.absolutePath,
                        rotationDegrees = updatedRotation,
                        corners = updatedCorners,
                        filter = updatedFilter
                    )
                }
                if (ok) {
                    val oldProcessed = File(page.processedFilePath)
                    if (oldProcessed.exists() && oldProcessed.absolutePath != page.rawFilePath) {
                        oldProcessed.delete()
                    }
                    val updatedPage = page.copy(
                        processedFilePath = nextOutFile.absolutePath,
                        rotationDegrees = updatedRotation,
                        corners = updatedCorners,
                        filter = updatedFilter
                    )
                    _draftPages.update { list ->
                        list.mapIndexed { i, item -> if (i == idx) updatedPage else item }
                    }
                }
            } finally {
                _isProcessing.value = false
            }
        }
    }

    fun defaultTitleForNow(language: AppLanguage): String {
        val ctx = getApplication<Application>()
        val prefix = ctx.getString(
            if (language == AppLanguage.SINHALA) R.string.default_doc_prefix_si else R.string.default_doc_prefix_en
        )
        val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())
        return "$prefix $stamp"
    }

    fun saveDraftSession(title: String, onSaved: (ScannedDocument) -> Unit) {
        val drafts = _draftPages.value
        if (drafts.isEmpty()) return

        viewModelScope.launch {
            _isProcessing.value = true
            try {
                val appendId = _appendToDocId.value
                val finalTitle = title.ifBlank { defaultTitleForNow(_language.value) }
                val savedDoc = if (appendId != null) {
                    repository.addPagesToDocument(appendId, drafts)
                } else {
                    repository.createDocument(
                        title = finalTitle,
                        draftPages = drafts,
                        autoExportToDownloads = _autoSaveDownloads.value
                    )
                }
                repository.clearDraftCache()
                _draftPages.value = emptyList()
                _activeDraftIndex.value = 0
                _appendToDocId.value = null
                _documents.value = repository.loadAllDocuments()

                if (savedDoc != null) {
                    val ctx = getApplication<Application>()
                    val msgId = if (appendId == null && _autoSaveDownloads.value) {
                        if (_language.value == AppLanguage.SINHALA) R.string.saved_downloads_toast_si else R.string.saved_downloads_toast_en
                    } else {
                        if (_language.value == AppLanguage.SINHALA) R.string.saved_success_si else R.string.saved_success_en
                    }
                    _snackbarMessage.value = ctx.getString(msgId)
                    onSaved(savedDoc)
                }
            } finally {
                _isProcessing.value = false
            }
        }
    }

    fun getDocument(docId: String): ScannedDocument? {
        return _documents.value.firstOrNull { it.id == docId }
    }

    fun renameDocument(docId: String, newTitle: String) {
        viewModelScope.launch {
            _isProcessing.value = true
            try {
                repository.renameDocument(docId, newTitle)
                _documents.value = repository.loadAllDocuments()
            } finally {
                _isProcessing.value = false
            }
        }
    }

    fun deleteDocument(docId: String, onDeleted: () -> Unit = {}) {
        viewModelScope.launch {
            _isProcessing.value = true
            try {
                repository.deleteDocument(docId)
                _documents.value = repository.loadAllDocuments()
                onDeleted()
            } finally {
                _isProcessing.value = false
            }
        }
    }

    fun deletePageFromDocument(docId: String, pageIndex: Int, onDocRemoved: () -> Unit = {}) {
        viewModelScope.launch {
            _isProcessing.value = true
            try {
                val updated = repository.deletePageFromDocument(docId, pageIndex)
                _documents.value = repository.loadAllDocuments()
                if (updated == null) {
                    onDocRemoved()
                }
            } finally {
                _isProcessing.value = false
            }
        }
    }

    fun movePageInDocument(docId: String, fromIndex: Int, toIndex: Int) {
        viewModelScope.launch {
            _isProcessing.value = true
            try {
                repository.movePageInDocument(docId, fromIndex, toIndex)
                _documents.value = repository.loadAllDocuments()
            } finally {
                _isProcessing.value = false
            }
        }
    }

    fun savePdfToDownloads(doc: ScannedDocument, onNeedSafFallback: () -> Unit) {
        viewModelScope.launch {
            _isProcessing.value = true
            try {
                val res = repository.savePdfToPublicDownloads(doc)
                if (res.isSuccess) {
                    val ctx = getApplication<Application>()
                    val msgRes = if (_language.value == AppLanguage.SINHALA) {
                        R.string.saved_downloads_toast_si
                    } else {
                        R.string.saved_downloads_toast_en
                    }
                    _snackbarMessage.value = ctx.getString(msgRes)
                } else {
                    onNeedSafFallback()
                }
            } finally {
                _isProcessing.value = false
            }
        }
    }

    fun savePagesToGallery(doc: ScannedDocument, onNeedSafFallback: () -> Unit) {
        viewModelScope.launch {
            _isProcessing.value = true
            try {
                val res = repository.savePagesToPublicGallery(doc)
                if (res.isSuccess) {
                    val ctx = getApplication<Application>()
                    val msgRes = if (_language.value == AppLanguage.SINHALA) {
                        R.string.saved_gallery_toast_si
                    } else {
                        R.string.saved_gallery_toast_en
                    }
                    _snackbarMessage.value = ctx.getString(msgRes)
                } else {
                    onNeedSafFallback()
                }
            } finally {
                _isProcessing.value = false
            }
        }
    }

    fun exportPdfToSafUri(doc: ScannedDocument, destUri: Uri) {
        viewModelScope.launch {
            _isProcessing.value = true
            try {
                val res = repository.exportPdfToUri(doc, destUri)
                if (res.isSuccess) {
                    val ctx = getApplication<Application>()
                    val msgRes = if (_language.value == AppLanguage.SINHALA) {
                        R.string.saved_success_si
                    } else {
                        R.string.saved_success_en
                    }
                    _snackbarMessage.value = ctx.getString(msgRes)
                }
            } finally {
                _isProcessing.value = false
            }
        }
    }

    fun createSharePdfIntent(doc: ScannedDocument): Intent {
        val ctx = getApplication<Application>()
        val titleRes = if (_language.value == AppLanguage.SINHALA) {
            R.string.share_chooser_si
        } else {
            R.string.share_chooser_en
        }
        return repository.createSharePdfIntent(doc, ctx.getString(titleRes))
    }

    fun createShareImagesIntent(doc: ScannedDocument): Intent {
        val ctx = getApplication<Application>()
        val titleRes = if (_language.value == AppLanguage.SINHALA) {
            R.string.share_chooser_si
        } else {
            R.string.share_chooser_en
        }
        return repository.createShareImagesIntent(doc, ctx.getString(titleRes))
    }

    fun suggestedPdfFileName(doc: ScannedDocument): String {
        return repository.sanitizeFileName(doc.title) + ".pdf"
    }

    private data class QuadTuple(
        val docs: List<ScannedDocument>,
        val query: String,
        val lang: AppLanguage,
        val autoSave: Boolean
    )

    private data class DraftTuple(
        val drafts: List<DraftPage>,
        val idx: Int,
        val appendId: String?,
        val busy: Boolean,
        val msg: String?
    )
}
