package com.danielbarrantesv.dashcam.util

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

/**
 * File helpers for videos, photos, reports, exports.
 * All under app-specific external storage (no MANAGE_EXTERNAL_STORAGE needed).
 */
object FileUtils {

    fun videosDir(ctx: Context): File =
        File(ctx.getExternalFilesDir(null), "videos").apply { if (!exists()) mkdirs() }

    fun reportsDir(ctx: Context): File =
        File(ctx.getExternalFilesDir(null), "reports").apply { if (!exists()) mkdirs() }

    fun photosDir(ctx: Context): File =
        File(ctx.getExternalFilesDir(null), "photos").apply { if (!exists()) mkdirs() }

    fun exportsDir(ctx: Context): File =
        File(ctx.getExternalFilesDir(null), "exports").apply { if (!exists()) mkdirs() }

    fun tempDir(ctx: Context): File =
        File(ctx.cacheDir, "temp").apply { if (!exists()) mkdirs() }

    fun videoFileForSegment(ctx: Context, tripId: Long, segmentIndex: Int): File =
        File(videosDir(ctx), "trip_${tripId}_seg_${String.format("%04d", segmentIndex)}.mp4")

    fun photoFile(ctx: Context, tripId: Long, ts: Long): File =
        File(photosDir(ctx), "trip_${tripId}_photo_${ts}.jpg")

    fun reportFile(ctx: Context, tripId: Long, ts: Long): File =
        File(reportsDir(ctx), "report_trip_${tripId}_${ts}.pdf")

    fun thumbnailFileForSegment(ctx: Context, segmentId: Long): File =
        File(tempDir(ctx), "thumb_${segmentId}.jpg")

    fun fileUri(ctx: Context, file: File): Uri =
        FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", file)

    /** Returns true if deletion succeeded. */
    fun safeDelete(file: File?): Boolean = file?.delete() ?: false

    fun dirSize(file: File?): Long {
        if (file == null || !file.exists()) return 0
        if (file.isFile) return file.length()
        return file.listFiles()?.sumOf { dirSize(it) } ?: 0
    }

    fun formatBytes(bytes: Long): String = when {
        bytes >= 1_073_741_824 -> "%.2f GB".format(bytes / 1_073_741_824.0)
        bytes >= 1_048_576 -> "%.1f MB".format(bytes / 1_048_576.0)
        bytes >= 1024 -> "%.1f KB".format(bytes / 1024.0)
        else -> "$bytes B"
    }
}
