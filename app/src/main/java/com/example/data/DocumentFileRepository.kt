package com.example.data

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.UUID

private val Context.prefsDataStore: DataStore<Preferences> by preferencesDataStore(name = "paperscan_prefs")

/**
 * Pure filesystem-backed document repository.
 * Uses NO database (no Room, no SQLite, no cloud DB). Every scanned document is stored
 * directly on the phone inside filesDir/scanned_docs/<doc_id>/ as JPEG pages, a PDF file,
 * and a lightweight doc_info.json sidecar, with optional zero-permission export to Downloads/PaperScan.
 */
class DocumentFileRepository(private val context: Context) {

    private val scansRootDir: File
        get() = File(context.filesDir, "scanned_docs").apply { mkdirs() }

    private val draftCacheDir: File
        get() = File(context.cacheDir, "draft_scans").apply { mkdirs() }

    private val authority: String
        get() = "${context.packageName}.fileprovider"

    private val keyLanguage = stringPreferencesKey("app_language")
    private val keyAutoSaveDownloads = booleanPreferencesKey("auto_save_downloads")

    val languageFlow: Flow<AppLanguage> = context.prefsDataStore.data.map { prefs ->
        AppLanguage.fromCode(prefs[keyLanguage])
    }

    val autoSaveDownloadsFlow: Flow<Boolean> = context.prefsDataStore.data.map { prefs ->
        prefs[keyAutoSaveDownloads] ?: (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
    }

    suspend fun setLanguage(language: AppLanguage) {
        context.prefsDataStore.edit { it[keyLanguage] = language.code }
    }

    suspend fun setAutoSaveDownloads(enabled: Boolean) {
        context.prefsDataStore.edit { it[keyAutoSaveDownloads] = enabled }
    }

    fun createTempDraftFile(prefix: String = "raw", ext: String = "jpg"): File {
        val name = "${prefix}_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(6)}.$ext"
        return File(draftCacheDir, name)
    }

    fun clearDraftCache() {
        draftCacheDir.listFiles()?.forEach { it.delete() }
    }

    suspend fun loadAllDocuments(): List<ScannedDocument> = withContext(Dispatchers.IO) {
        val dirs = scansRootDir.listFiles { file -> file.isDirectory } ?: return@withContext emptyList()
        dirs.mapNotNull { dir -> readDocumentFromDir(dir) }
            .sortedByDescending { it.updatedAtMillis }
    }

    suspend fun getDocumentById(docId: String): ScannedDocument? = withContext(Dispatchers.IO) {
        val dir = File(scansRootDir, docId)
        if (!dir.exists() || !dir.isDirectory) return@withContext null
        readDocumentFromDir(dir)
    }

    private fun readDocumentFromDir(dir: File): ScannedDocument? {
        val metaFile = File(dir, "doc_info.json")
        if (!metaFile.exists()) return null
        return try {
            val json = JSONObject(metaFile.readText())
            val id = json.optString("id", dir.name)
            val title = json.optString("title", "Scanned Doc")
            val createdAt = json.optLong("createdAtMillis", dir.lastModified())
            val updatedAt = json.optLong("updatedAtMillis", createdAt)
            val filter = ScanFilter.fromId(json.optString("filter"))
            val pagesJson = json.optJSONArray("pages") ?: JSONArray()
            val pagePaths = buildList {
                for (i in 0 until pagesJson.length()) {
                    val fileName = pagesJson.optString(i)
                    val pageFile = File(dir, fileName)
                    if (pageFile.exists()) add(pageFile.absolutePath)
                }
            }
            if (pagePaths.isEmpty()) return null
            val pdfName = json.optString("pdfFileName", "document.pdf")
            val pdfFile = File(dir, pdfName)
            if (!pdfFile.exists()) {
                ImageProcessor.generatePdf(pagePaths, pdfFile)
            }
            val totalSize = pagePaths.sumOf { File(it).length() } +
                if (pdfFile.exists()) pdfFile.length() else 0L

            ScannedDocument(
                id = id,
                title = title,
                createdAtMillis = createdAt,
                updatedAtMillis = updatedAt,
                pagePaths = pagePaths,
                pdfPath = pdfFile.absolutePath,
                totalSizeBytes = totalSize,
                filter = filter
            )
        } catch (_: Exception) {
            null
        }
    }

    suspend fun createDocument(
        title: String,
        draftPages: List<DraftPage>,
        autoExportToDownloads: Boolean
    ): ScannedDocument? = withContext(Dispatchers.IO) {
        if (draftPages.isEmpty()) return@withContext null
        val docId = "doc_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(4)}"
        val docDir = File(scansRootDir, docId).apply { mkdirs() }
        val now = System.currentTimeMillis()

        val savedPageNames = mutableListOf<String>()
        val savedPagePaths = mutableListOf<String>()

        draftPages.forEachIndexed { index, draft ->
            val srcFile = File(draft.processedFilePath).takeIf { it.exists() } ?: File(draft.rawFilePath)
            if (srcFile.exists()) {
                val pageName = "page_${String.format("%03d", index + 1)}.jpg"
                val destFile = File(docDir, pageName)
                srcFile.copyTo(destFile, overwrite = true)
                savedPageNames.add(pageName)
                savedPagePaths.add(destFile.absolutePath)
            }
        }

        if (savedPagePaths.isEmpty()) {
            docDir.deleteRecursively()
            return@withContext null
        }

        val safePdfName = sanitizeFileName(title) + ".pdf"
        val pdfFile = File(docDir, safePdfName)
        ImageProcessor.generatePdf(savedPagePaths, pdfFile)

        val filterUsed = draftPages.lastOrNull()?.filter ?: ScanFilter.MAGIC_COLOR
        writeMetadata(
            dir = docDir,
            id = docId,
            title = title.trim(),
            createdAt = now,
            updatedAt = now,
            pageNames = savedPageNames,
            pdfFileName = safePdfName,
            filter = filterUsed
        )

        val doc = readDocumentFromDir(docDir)
        if (doc != null && autoExportToDownloads && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            savePdfToPublicDownloadsInternal(doc)
        }
        doc
    }

    suspend fun addPagesToDocument(
        docId: String,
        newDraftPages: List<DraftPage>
    ): ScannedDocument? = withContext(Dispatchers.IO) {
        val docDir = File(scansRootDir, docId)
        val existing = readDocumentFromDir(docDir) ?: return@withContext null
        val existingNames = existing.pagePaths.map { File(it).name }.toMutableList()
        val existingPaths = existing.pagePaths.toMutableList()

        var nextSeq = existingNames.size + 1
        newDraftPages.forEach { draft ->
            val srcFile = File(draft.processedFilePath).takeIf { it.exists() } ?: File(draft.rawFilePath)
            if (srcFile.exists()) {
                val pageName = "page_${System.currentTimeMillis()}_${nextSeq++}.jpg"
                val destFile = File(docDir, pageName)
                srcFile.copyTo(destFile, overwrite = true)
                existingNames.add(pageName)
                existingPaths.add(destFile.absolutePath)
            }
        }

        val pdfFile = File(existing.pdfPath)
        ImageProcessor.generatePdf(existingPaths, pdfFile)
        writeMetadata(
            dir = docDir,
            id = existing.id,
            title = existing.title,
            createdAt = existing.createdAtMillis,
            updatedAt = System.currentTimeMillis(),
            pageNames = existingNames,
            pdfFileName = pdfFile.name,
            filter = existing.filter
        )
        readDocumentFromDir(docDir)
    }

    suspend fun deletePageFromDocument(docId: String, pageIndex: Int): ScannedDocument? =
        withContext(Dispatchers.IO) {
            val docDir = File(scansRootDir, docId)
            val existing = readDocumentFromDir(docDir) ?: return@withContext null
            if (pageIndex !in existing.pagePaths.indices) return@withContext existing

            if (existing.pagePaths.size <= 1) {
                docDir.deleteRecursively()
                return@withContext null
            }

            val removedPath = existing.pagePaths[pageIndex]
            File(removedPath).delete()

            val remainingPaths = existing.pagePaths.filterIndexed { i, _ -> i != pageIndex }
            val remainingNames = remainingPaths.map { File(it).name }
            val pdfFile = File(existing.pdfPath)
            ImageProcessor.generatePdf(remainingPaths, pdfFile)

            writeMetadata(
                dir = docDir,
                id = existing.id,
                title = existing.title,
                createdAt = existing.createdAtMillis,
                updatedAt = System.currentTimeMillis(),
                pageNames = remainingNames,
                pdfFileName = pdfFile.name,
                filter = existing.filter
            )
            readDocumentFromDir(docDir)
        }

    suspend fun movePageInDocument(docId: String, fromIndex: Int, toIndex: Int): ScannedDocument? =
        withContext(Dispatchers.IO) {
            val docDir = File(scansRootDir, docId)
            val existing = readDocumentFromDir(docDir) ?: return@withContext null
            if (fromIndex !in existing.pagePaths.indices || toIndex !in existing.pagePaths.indices) {
                return@withContext existing
            }
            val reorderedPaths = existing.pagePaths.toMutableList()
            val moved = reorderedPaths.removeAt(fromIndex)
            reorderedPaths.add(toIndex, moved)

            val pdfFile = File(existing.pdfPath)
            ImageProcessor.generatePdf(reorderedPaths, pdfFile)
            writeMetadata(
                dir = docDir,
                id = existing.id,
                title = existing.title,
                createdAt = existing.createdAtMillis,
                updatedAt = System.currentTimeMillis(),
                pageNames = reorderedPaths.map { File(it).name },
                pdfFileName = pdfFile.name,
                filter = existing.filter
            )
            readDocumentFromDir(docDir)
        }

    suspend fun renameDocument(docId: String, newTitle: String): ScannedDocument? =
        withContext(Dispatchers.IO) {
            val cleanTitle = newTitle.trim()
            if (cleanTitle.isEmpty()) return@withContext getDocumentById(docId)
            val docDir = File(scansRootDir, docId)
            val existing = readDocumentFromDir(docDir) ?: return@withContext null

            val oldPdf = File(existing.pdfPath)
            val newPdfName = sanitizeFileName(cleanTitle) + ".pdf"
            val newPdf = File(docDir, newPdfName)
            if (oldPdf.exists() && oldPdf.absolutePath != newPdf.absolutePath) {
                oldPdf.renameTo(newPdf)
            }
            if (!newPdf.exists()) {
                ImageProcessor.generatePdf(existing.pagePaths, newPdf)
            }

            writeMetadata(
                dir = docDir,
                id = existing.id,
                title = cleanTitle,
                createdAt = existing.createdAtMillis,
                updatedAt = System.currentTimeMillis(),
                pageNames = existing.pagePaths.map { File(it).name },
                pdfFileName = newPdfName,
                filter = existing.filter
            )
            readDocumentFromDir(docDir)
        }

    suspend fun deleteDocument(docId: String) = withContext(Dispatchers.IO) {
        val docDir = File(scansRootDir, docId)
        if (docDir.exists()) {
            docDir.deleteRecursively()
        }
    }

    suspend fun savePdfToPublicDownloads(doc: ScannedDocument): Result<String> =
        withContext(Dispatchers.IO) {
            savePdfToPublicDownloadsInternal(doc)
        }

    private fun savePdfToPublicDownloadsInternal(doc: ScannedDocument): Result<String> {
        val pdfFile = File(doc.pdfPath)
        if (!pdfFile.exists()) return Result.failure(IllegalStateException("PDF missing"))

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return Result.failure(UnsupportedOperationException("SAF_REQUIRED"))
        }

        return try {
            val fileName = "${sanitizeFileName(doc.title)}_${System.currentTimeMillis() % 10000}.pdf"
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                put(MediaStore.Downloads.MIME_TYPE, "application/pdf")
                put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/PaperScan")
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: return Result.failure(IllegalStateException("Cannot create Downloads entry"))

            resolver.openOutputStream(uri)?.use { out ->
                FileInputStream(pdfFile).use { input ->
                    input.copyTo(out)
                }
            }
            values.clear()
            values.put(MediaStore.Downloads.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            Result.success(fileName)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun savePagesToPublicGallery(doc: ScannedDocument): Result<Int> =
        withContext(Dispatchers.IO) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                return@withContext Result.failure(UnsupportedOperationException("SAF_REQUIRED"))
            }
            try {
                val resolver = context.contentResolver
                var savedCount = 0
                doc.pagePaths.forEachIndexed { idx, path ->
                    val pageFile = File(path)
                    if (!pageFile.exists()) return@forEachIndexed
                    val name = "${sanitizeFileName(doc.title)}_p${idx + 1}.jpg"
                    val values = ContentValues().apply {
                        put(MediaStore.Images.Media.DISPLAY_NAME, name)
                        put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                        put(
                            MediaStore.Images.Media.RELATIVE_PATH,
                            "${Environment.DIRECTORY_PICTURES}/PaperScan"
                        )
                        put(MediaStore.Images.Media.IS_PENDING, 1)
                    }
                    val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                    if (uri != null) {
                        resolver.openOutputStream(uri)?.use { out ->
                            FileInputStream(pageFile).use { input -> input.copyTo(out) }
                        }
                        values.clear()
                        values.put(MediaStore.Images.Media.IS_PENDING, 0)
                        resolver.update(uri, values, null, null)
                        savedCount++
                    }
                }
                Result.success(savedCount)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    suspend fun exportPdfToUri(doc: ScannedDocument, destUri: Uri): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                val pdfFile = File(doc.pdfPath)
                context.contentResolver.openOutputStream(destUri)?.use { out ->
                    FileInputStream(pdfFile).use { input -> input.copyTo(out) }
                }
                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    fun createSharePdfIntent(doc: ScannedDocument, chooserTitle: String): Intent {
        val pdfFile = File(doc.pdfPath)
        val uri = FileProvider.getUriForFile(context, authority, pdfFile)
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, doc.title)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return Intent.createChooser(sendIntent, chooserTitle)
    }

    fun createShareImagesIntent(doc: ScannedDocument, chooserTitle: String): Intent {
        val uris = ArrayList<Uri>(
            doc.pagePaths.mapNotNull { path ->
                val f = File(path)
                if (f.exists()) FileProvider.getUriForFile(context, authority, f) else null
            }
        )
        val sendIntent = if (uris.size <= 1) {
            Intent(Intent.ACTION_SEND).apply {
                type = "image/jpeg"
                uris.firstOrNull()?.let { putExtra(Intent.EXTRA_STREAM, it) }
                putExtra(Intent.EXTRA_SUBJECT, doc.title)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        } else {
            Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                type = "image/jpeg"
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
                putExtra(Intent.EXTRA_SUBJECT, doc.title)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }
        return Intent.createChooser(sendIntent, chooserTitle)
    }

    private fun writeMetadata(
        dir: File,
        id: String,
        title: String,
        createdAt: Long,
        updatedAt: Long,
        pageNames: List<String>,
        pdfFileName: String,
        filter: ScanFilter
    ) {
        val pagesArray = JSONArray().apply {
            pageNames.forEach { put(it) }
        }
        val json = JSONObject().apply {
            put("id", id)
            put("title", title)
            put("createdAtMillis", createdAt)
            put("updatedAtMillis", updatedAt)
            put("pdfFileName", pdfFileName)
            put("filter", filter.id)
            put("pages", pagesArray)
        }
        FileOutputStream(File(dir, "doc_info.json")).use { out ->
            out.write(json.toString(2).toByteArray(Charsets.UTF_8))
        }
    }

    fun sanitizeFileName(raw: String): String {
        val cleaned = raw.trim()
            .replace(Regex("[\\\\/:*?\"<>|]"), "_")
            .replace(Regex("\\s+"), "_")
            .take(50)
        return cleaned.ifBlank { "PaperScan_Doc" }
    }
}
