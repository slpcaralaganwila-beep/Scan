package com.example.data

enum class ScanFilter(val id: String) {
    MAGIC_COLOR("magic_color"),
    BLACK_WHITE("black_white"),
    GRAYSCALE("grayscale"),
    ORIGINAL("original");

    companion object {
        fun fromId(id: String?): ScanFilter =
            entries.firstOrNull { it.id == id } ?: MAGIC_COLOR
    }
}

enum class AppLanguage(val code: String) {
    SINHALA("si"),
    ENGLISH("en");

    companion object {
        fun fromCode(code: String?): AppLanguage =
            if (code == ENGLISH.code) ENGLISH else SINHALA
    }
}

data class NormalizedPoint(
    val x: Float,
    val y: Float
)

data class QuadCorners(
    val topLeft: NormalizedPoint,
    val topRight: NormalizedPoint,
    val bottomRight: NormalizedPoint,
    val bottomLeft: NormalizedPoint
) {
    fun rotateClockwise(): QuadCorners {
        // When an image rotates 90 deg clockwise: (x, y) -> (1 - y, x)
        fun rot(p: NormalizedPoint) = NormalizedPoint(
            x = (1f - p.y).coerceIn(0f, 1f),
            y = p.x.coerceIn(0f, 1f)
        )
        return QuadCorners(
            topLeft = rot(bottomLeft),
            topRight = rot(topLeft),
            bottomRight = rot(topRight),
            bottomLeft = rot(bottomRight)
        )
    }

    companion object {
        val DEFAULT = QuadCorners(
            topLeft = NormalizedPoint(0.06f, 0.06f),
            topRight = NormalizedPoint(0.94f, 0.06f),
            bottomRight = NormalizedPoint(0.94f, 0.94f),
            bottomLeft = NormalizedPoint(0.06f, 0.94f)
        )

        val FULL = QuadCorners(
            topLeft = NormalizedPoint(0.01f, 0.01f),
            topRight = NormalizedPoint(0.99f, 0.01f),
            bottomRight = NormalizedPoint(0.99f, 0.99f),
            bottomLeft = NormalizedPoint(0.01f, 0.99f)
        )
    }
}

data class DraftPage(
    val id: String,
    val rawFilePath: String,
    val processedFilePath: String,
    val rotationDegrees: Int = 0,
    val corners: QuadCorners = QuadCorners.DEFAULT,
    val filter: ScanFilter = ScanFilter.MAGIC_COLOR
)

data class ScannedDocument(
    val id: String,
    val title: String,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
    val pagePaths: List<String>,
    val pdfPath: String,
    val totalSizeBytes: Long,
    val filter: ScanFilter
)
