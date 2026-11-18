package com.example

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.test.core.app.ApplicationProvider
import com.example.data.DocumentFileRepository
import com.example.data.DraftPage
import com.example.data.ImageProcessor
import com.example.data.QuadCorners
import com.example.data.ScanFilter
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.io.FileOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PaperScanRepositoryTest {

    @Test
    fun `offline image processing and zero-database file storage creates PDF and shares cleanly`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repo = DocumentFileRepository(context)

        // Create a synthetic printed-paper image in cache
        val rawFile = repo.createTempDraftFile(prefix = "test_raw", ext = "jpg")
        val bmp = Bitmap.createBitmap(400, 560, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawColor(Color.rgb(245, 243, 238))
        val paint = Paint().apply {
            color = Color.BLACK
            strokeWidth = 6f
        }
        canvas.drawLine(40f, 80f, 340f, 80f, paint)
        canvas.drawLine(40f, 140f, 300f, 140f, paint)
        FileOutputStream(rawFile).use { out ->
            bmp.compress(Bitmap.CompressFormat.JPEG, 90, out)
        }
        bmp.recycle()

        // Process perspective crop + B&W adaptive print filter
        val processedFile = repo.createTempDraftFile(prefix = "test_proc", ext = "jpg")
        val ok = ImageProcessor.processAndSavePage(
            rawPath = rawFile.absolutePath,
            outputPath = processedFile.absolutePath,
            rotationDegrees = 0,
            corners = QuadCorners.DEFAULT,
            filter = ScanFilter.BLACK_WHITE
        )
        assertTrue(ok)
        assertTrue(processedFile.exists() && processedFile.length() > 0L)

        // Save directly to phone filesystem (no database!)
        val draft = DraftPage(
            id = "p1",
            rawFilePath = rawFile.absolutePath,
            processedFilePath = processedFile.absolutePath,
            filter = ScanFilter.BLACK_WHITE
        )
        val saved = repo.createDocument(
            title = "Printed Receipt",
            draftPages = listOf(draft),
            autoExportToDownloads = false
        )
        assertTrue(saved != null)
        assertEquals("Printed Receipt", saved!!.title)
        assertEquals(1, saved.pagePaths.size)
        assertTrue(File(saved.pdfPath).exists())

        // Verify loading from filesystem returns saved doc
        val allDocs = repo.loadAllDocuments()
        assertTrue(allDocs.any { it.id == saved.id })

        // Verify rename & delete
        val renamed = repo.renameDocument(saved.id, "Updated Receipt")
        assertEquals("Updated Receipt", renamed?.title)
        assertTrue(File(renamed!!.pdfPath).exists())

        repo.deleteDocument(saved.id)
        val afterDelete = repo.loadAllDocuments()
        assertTrue(afterDelete.none { it.id == saved.id })
    }
}
