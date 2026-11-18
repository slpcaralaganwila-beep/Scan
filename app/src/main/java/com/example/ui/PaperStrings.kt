package com.example.ui

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.example.data.AppLanguage

@Composable
fun tr(
    language: AppLanguage,
    @StringRes sinhalaRes: Int,
    @StringRes englishRes: Int
): String {
    return stringResource(id = if (language == AppLanguage.SINHALA) sinhalaRes else englishRes)
}

@Composable
fun tr(
    language: AppLanguage,
    @StringRes sinhalaRes: Int,
    @StringRes englishRes: Int,
    vararg formatArgs: Any
): String {
    val resId = if (language == AppLanguage.SINHALA) sinhalaRes else englishRes
    return stringResource(id = resId, *formatArgs)
}

fun formatByteSize(bytes: Long): String {
    if (bytes < 1024L) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024.0) return String.format("%.0f KB", kb)
    val mb = kb / 1024.0
    return String.format("%.1f MB", mb)
}
