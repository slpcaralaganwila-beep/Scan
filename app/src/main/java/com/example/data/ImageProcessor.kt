package com.example.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import android.media.ExifInterface
import android.net.Uri
import java.io.File
import java.io.FileOutputStream
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

object ImageProcessor {

    private const val MAX_DECODE_DIMENSION = 2200
    private const val MAX_OUTPUT_DIMENSION = 2000

    fun copyUriToTempFile(context: Context, uri: Uri, destFile: File): Boolean {
        return try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(destFile).use { output ->
                    input.copyTo(output)
                }
            }
            destFile.exists() && destFile.length() > 0L
        } catch (_: Exception) {
            false
        }
    }

    fun decodeSampledAndOriented(path: String, maxDim: Int = MAX_DECODE_DIMENSION): Bitmap? {
        val file = File(path)
        if (!file.exists()) return null

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        val rawW = bounds.outWidth
        val rawH = bounds.outHeight
        if (rawW <= 0 || rawH <= 0) return null

        var sampleSize = 1
        while ((rawW / sampleSize) > maxDim || (rawH / sampleSize) > maxDim) {
            sampleSize *= 2
        }

        val decodeOpts = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val decoded = BitmapFactory.decodeFile(path, decodeOpts) ?: return null

        val exifOrientation = try {
            ExifInterface(path).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )
        } catch (_: Exception) {
            ExifInterface.ORIENTATION_NORMAL
        }

        val matrix = Matrix()
        when (exifOrientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            else -> return decoded
        }

        val oriented = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
        if (oriented !== decoded) {
            decoded.recycle()
        }
        return oriented
    }

    fun rotateBitmap(source: Bitmap, degrees: Int): Bitmap {
        val normalized = ((degrees % 360) + 360) % 360
        if (normalized == 0) return source
        val matrix = Matrix().apply { postRotate(normalized.toFloat()) }
        return Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
    }

    fun warpPerspective(source: Bitmap, corners: QuadCorners): Bitmap {
        val w = source.width.toFloat()
        val h = source.height.toFloat()

        val tlX = corners.topLeft.x * w
        val tlY = corners.topLeft.y * h
        val trX = corners.topRight.x * w
        val trY = corners.topRight.y * h
        val brX = corners.bottomRight.x * w
        val brY = corners.bottomRight.y * h
        val blX = corners.bottomLeft.x * w
        val blY = corners.bottomLeft.y * h

        val topWidth = hypot(trX - tlX, trY - tlY)
        val bottomWidth = hypot(brX - blX, brY - blY)
        val leftHeight = hypot(blX - tlX, blY - tlY)
        val rightHeight = hypot(brX - trX, brY - trY)

        var targetW = max(topWidth, bottomWidth).roundToInt().coerceAtLeast(120)
        var targetH = max(leftHeight, rightHeight).roundToInt().coerceAtLeast(120)

        val maxSide = max(targetW, targetH)
        if (maxSide > MAX_OUTPUT_DIMENSION) {
            val scale = MAX_OUTPUT_DIMENSION.toFloat() / maxSide.toFloat()
            targetW = (targetW * scale).roundToInt().coerceAtLeast(120)
            targetH = (targetH * scale).roundToInt().coerceAtLeast(120)
        }

        val srcPts = floatArrayOf(
            tlX, tlY,
            trX, trY,
            brX, brY,
            blX, blY
        )
        val dstPts = floatArrayOf(
            0f, 0f,
            targetW.toFloat(), 0f,
            targetW.toFloat(), targetH.toFloat(),
            0f, targetH.toFloat()
        )

        val matrix = Matrix()
        val mapped = matrix.setPolyToPoly(srcPts, 0, dstPts, 0, 4)
        if (!mapped) {
            return source.copy(Bitmap.Config.ARGB_8888, false)
        }

        val output = Bitmap.createBitmap(targetW, targetH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        canvas.drawColor(Color.WHITE)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        canvas.drawBitmap(source, matrix, paint)
        return output
    }

    fun applyScanFilter(source: Bitmap, filter: ScanFilter): Bitmap {
        return when (filter) {
            ScanFilter.ORIGINAL -> source.copy(Bitmap.Config.ARGB_8888, false)
            ScanFilter.GRAYSCALE -> applyColorMatrixFilter(source, contrast = 1.32f, brightness = 12f, saturation = 0f)
            ScanFilter.MAGIC_COLOR -> applyMagicPaperEnhancement(source)
            ScanFilter.BLACK_WHITE -> applyAdaptiveBlackAndWhite(source)
        }
    }

    private fun applyColorMatrixFilter(
        source: Bitmap,
        contrast: Float,
        brightness: Float,
        saturation: Float
    ): Bitmap {
        val output = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        val satMatrix = ColorMatrix().apply { setSaturation(saturation) }
        val translate = (-0.5f * contrast + 0.5f) * 255f + brightness
        val contrastMatrix = ColorMatrix(
            floatArrayOf(
                contrast, 0f, 0f, 0f, translate,
                0f, contrast, 0f, 0f, translate,
                0f, 0f, contrast, 0f, translate,
                0f, 0f, 0f, 1f, 0f
            )
        )
        satMatrix.postConcat(contrastMatrix)
        paint.colorFilter = ColorMatrixColorFilter(satMatrix)
        canvas.drawBitmap(source, 0f, 0f, paint)
        return output
    }

    /**
     * Brightens off-white paper backgrounds to clean white while sharpening printed text and
     * keeping colored stamps, highlights, and logos vivid.
     */
    private fun applyMagicPaperEnhancement(source: Bitmap): Bitmap {
        val w = source.width
        val h = source.height
        val pixels = IntArray(w * h)
        source.getPixels(pixels, 0, w, 0, 0, w, h)

        for (i in pixels.indices) {
            val c = pixels[i]
            val r = (c shr 16) and 0xFF
            val g = (c shr 8) and 0xFF
            val b = c and 0xFF

            // Slight S-curve + paper white stretch
            val nr = enhanceChannel(r)
            val ng = enhanceChannel(g)
            val nb = enhanceChannel(b)

            // Subtle saturation boost so ink & stamps stay crisp
            val avg = (nr + ng + nb) / 3
            val fr = (avg + ((nr - avg) * 1.18f)).roundToInt().coerceIn(0, 255)
            val fg = (avg + ((ng - avg) * 1.18f)).roundToInt().coerceIn(0, 255)
            val fb = (avg + ((nb - avg) * 1.18f)).roundToInt().coerceIn(0, 255)

            pixels[i] = (0xFF shl 24) or (fr shl 16) or (fg shl 8) or fb
        }

        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        out.setPixels(pixels, 0, w, 0, 0, w, h)
        return out
    }

    private fun enhanceChannel(v: Int): Int {
        // Stretch highlights above ~205 toward pure white and deepen dark ink below ~75
        val normalized = v / 255f
        val contrasted = ((normalized - 0.5f) * 1.28f + 0.54f).coerceIn(0f, 1f)
        return if (contrasted > 0.84f) 255 else (contrasted * 255f).roundToInt().coerceIn(0, 255)
    }

    /**
     * Local adaptive thresholding using an integral image (summed-area table) so uneven
     * lighting or phone shadows on printed paper still yield sharp black text on clean white paper.
     */
    private fun applyAdaptiveBlackAndWhite(source: Bitmap): Bitmap {
        val w = source.width
        val h = source.height
        val pixels = IntArray(w * h)
        source.getPixels(pixels, 0, w, 0, 0, w, h)

        val gray = IntArray(w * h)
        val integral = LongArray((w + 1) * (h + 1))

        for (y in 0 until h) {
            var rowSum = 0L
            val rowOffset = y * w
            val intRow = (y + 1) * (w + 1)
            val prevIntRow = y * (w + 1)
            for (x in 0 until w) {
                val c = pixels[rowOffset + x]
                val r = (c shr 16) and 0xFF
                val g = (c shr 8) and 0xFF
                val b = c and 0xFF
                val lum = (77 * r + 150 * g + 29 * b) shr 8
                gray[rowOffset + x] = lum
                rowSum += lum
                integral[intRow + x + 1] = integral[prevIntRow + x + 1] + rowSum
            }
        }

        val radius = max(12, min(w, h) / 28)
        val thresholdFactor = 0.87f

        for (y in 0 until h) {
            val y1 = max(0, y - radius)
            val y2 = min(h - 1, y + radius)
            val rowOffset = y * w
            for (x in 0 until w) {
                val x1 = max(0, x - radius)
                val x2 = min(w - 1, x + radius)
                val count = (x2 - x1 + 1) * (y2 - y1 + 1)

                val sum = integral[(y2 + 1) * (w + 1) + (x2 + 1)] -
                    integral[y1 * (w + 1) + (x2 + 1)] -
                    integral[(y2 + 1) * (w + 1) + x1] +
                    integral[y1 * (w + 1) + x1]

                val localMean = sum.toFloat() / count.toFloat()
                val pixelLum = gray[rowOffset + x].toFloat()

                // Soft-edge document binarization to prevent jagged text
                val outVal = when {
                    pixelLum < localMean * (thresholdFactor - 0.08f) -> 18
                    pixelLum < localMean * thresholdFactor -> {
                        val ratio = (pixelLum - localMean * (thresholdFactor - 0.08f)) /
                            (localMean * 0.08f).coerceAtLeast(1f)
                        (18 + ratio * 150).roundToInt().coerceIn(18, 220)
                    }
                    else -> 255
                }
                pixels[rowOffset + x] = (0xFF shl 24) or (outVal shl 16) or (outVal shl 8) or outVal
            }
        }

        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        out.setPixels(pixels, 0, w, 0, 0, w, h)
        return out
    }

    fun processAndSavePage(
        rawPath: String,
        outputPath: String,
        rotationDegrees: Int,
        corners: QuadCorners,
        filter: ScanFilter
    ): Boolean {
        val base = decodeSampledAndOriented(rawPath) ?: return false
        var rotated: Bitmap? = null
        var warped: Bitmap? = null
        var filtered: Bitmap? = null
        return try {
            rotated = rotateBitmap(base, rotationDegrees)
            warped = warpPerspective(rotated, corners)
            filtered = applyScanFilter(warped, filter)
            val outFile = File(outputPath)
            outFile.parentFile?.mkdirs()
            FileOutputStream(outFile).use { fos ->
                filtered.compress(Bitmap.CompressFormat.JPEG, 92, fos)
            }
            true
        } catch (_: Exception) {
            false
        } finally {
            if (filtered != null && !filtered.isRecycled) filtered.recycle()
            if (warped != null && warped !== filtered && !warped.isRecycled) warped.recycle()
            if (rotated != null && rotated !== base && rotated !== warped && !rotated.isRecycled) rotated.recycle()
            if (!base.isRecycled) base.recycle()
        }
    }

    fun generatePdf(pagePaths: List<String>, outputPdfFile: File): Boolean {
        if (pagePaths.isEmpty()) return false
        val pdfDocument = PdfDocument()
        return try {
            val a4WidthPts = 595
            pagePaths.forEachIndexed { index, path ->
                val bmp = decodeSampledAndOriented(path, maxDim = 2000) ?: return@forEachIndexed
                try {
                    val aspect = bmp.height.toFloat() / bmp.width.toFloat().coerceAtLeast(1f)
                    val pageHeightPts = (a4WidthPts * aspect).roundToInt().coerceIn(220, 1400)
                    val pageInfo = PdfDocument.PageInfo.Builder(a4WidthPts, pageHeightPts, index + 1).create()
                    val page = pdfDocument.startPage(pageInfo)
                    val canvas = page.canvas
                    canvas.drawColor(Color.WHITE)
                    val srcRect = Rect(0, 0, bmp.width, bmp.height)
                    val dstRect = RectF(0f, 0f, a4WidthPts.toFloat(), pageHeightPts.toFloat())
                    val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
                    canvas.drawBitmap(bmp, srcRect, dstRect, paint)
                    pdfDocument.finishPage(page)
                } finally {
                    if (!bmp.isRecycled) bmp.recycle()
                }
            }
            outputPdfFile.parentFile?.mkdirs()
            FileOutputStream(outputPdfFile).use { out ->
                pdfDocument.writeTo(out)
            }
            outputPdfFile.exists() && outputPdfFile.length() > 0L
        } catch (_: Throwable) {
            writeFallbackJpegPdf(pagePaths, outputPdfFile)
        } finally {
            try {
                pdfDocument.close()
            } catch (_: Throwable) {
            }
        }
    }

    private fun writeFallbackJpegPdf(pagePaths: List<String>, outputPdfFile: File): Boolean {
        return try {
            outputPdfFile.parentFile?.mkdirs()
            val validPages = pagePaths.mapNotNull { path ->
                val f = File(path)
                if (!f.exists() || f.length() == 0L) return@mapNotNull null
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(path, bounds)
                val imgW = bounds.outWidth.coerceAtLeast(100)
                val imgH = bounds.outHeight.coerceAtLeast(100)
                Triple(f.readBytes(), imgW, imgH)
            }
            if (validPages.isEmpty()) return false

            val out = java.io.ByteArrayOutputStream()
            val offsets = mutableListOf<Int>()
            fun writeAscii(s: String) = out.write(s.toByteArray(Charsets.US_ASCII))

            writeAscii("%PDF-1.4\n")

            val pageCount = validPages.size
            val pageObjIds = (0 until pageCount).map { 3 + it * 3 }
            val kidsStr = pageObjIds.joinToString(" ") { "$it 0 R" }

            // Obj 1: Catalog
            offsets.add(out.size())
            writeAscii("1 0 obj\n<< /Type /Catalog /Pages 2 0 R >>\nendobj\n")

            // Obj 2: Pages
            offsets.add(out.size())
            writeAscii("2 0 obj\n<< /Type /Pages /Kids [$kidsStr] /Count $pageCount >>\nendobj\n")

            val a4W = 595
            validPages.forEachIndexed { idx, (jpegBytes, imgW, imgH) ->
                val pageId = 3 + idx * 3
                val imgId = 4 + idx * 3
                val contentId = 5 + idx * 3
                val pageH = ((a4W.toFloat() * imgH / imgW).roundToInt()).coerceIn(220, 1400)
                val contentStream = "q\n$a4W 0 0 $pageH 0 0 cm\n/Im0 Do\nQ\n"
                val contentBytes = contentStream.toByteArray(Charsets.US_ASCII)

                offsets.add(out.size())
                writeAscii(
                    "$pageId 0 obj\n" +
                        "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 $a4W $pageH] " +
                        "/Resources << /XObject << /Im0 $imgId 0 R >> >> /Contents $contentId 0 R >>\n" +
                        "endobj\n"
                )

                offsets.add(out.size())
                writeAscii(
                    "$imgId 0 obj\n" +
                        "<< /Type /XObject /Subtype /Image /Width $imgW /Height $imgH " +
                        "/ColorSpace /DeviceRGB /BitsPerComponent 8 /Filter /DCTDecode " +
                        "/Length ${jpegBytes.size} >>\nstream\n"
                )
                out.write(jpegBytes)
                writeAscii("\nendstream\nendobj\n")

                offsets.add(out.size())
                writeAscii(
                    "$contentId 0 obj\n<< /Length ${contentBytes.size} >>\nstream\n"
                )
                out.write(contentBytes)
                writeAscii("endstream\nendobj\n")
            }

            val xrefStart = out.size()
            val totalObjects = offsets.size + 1
            writeAscii("xref\n0 $totalObjects\n")
            writeAscii("0000000000 65535 f \n")
            offsets.forEach { offset ->
                writeAscii(String.format("%010d 00000 n \n", offset))
            }
            writeAscii("trailer\n<< /Size $totalObjects /Root 1 0 R >>\nstartxref\n$xrefStart\n%%EOF\n")

            FileOutputStream(outputPdfFile).use { fos ->
                fos.write(out.toByteArray())
            }
            outputPdfFile.exists() && outputPdfFile.length() > 0L
        } catch (_: Throwable) {
            false
        }
    }
}
