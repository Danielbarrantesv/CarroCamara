package com.danielbarrantesv.dashcam.util

import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.core.content.FileProvider
import androidx.documentfile.provider.DocumentFile
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

/**
 * File helpers for videos, photos, reports, exports.
 * Supports default app-specific storage and custom SAF locations.
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

    fun videoFileForSegment(ctx: Context, tripId: Long, segmentIndex: Int, lens: String = "Back"): File {
        val ts = System.currentTimeMillis()
        val formatted = formatTimestampForFilename(ts)
        return File(videosDir(ctx), "Trip-Cam-$lens-$formatted.mp4")
    }

    /** Returns a filename-friendly timestamp: D-M-YYYY-HH:MM (no colons, filesystem-safe). */
    fun formatTimestampForFilename(ts: Long): String {
        val cal = java.util.Calendar.getInstance().apply { timeInMillis = ts }
        val day = cal.get(java.util.Calendar.DAY_OF_MONTH)
        val month = cal.get(java.util.Calendar.MONTH) + 1
        val year = cal.get(java.util.Calendar.YEAR)
        val hour = cal.get(java.util.Calendar.HOUR_OF_DAY)
        val minute = cal.get(java.util.Calendar.MINUTE)
        return "%d-%d-%d-%02d%02d".format(day, month, year, hour, minute)
    }

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

    /** Human-readable description of the default storage location. */
    fun defaultStorageDescription(ctx: Context): String {
        val dir = ctx.getExternalFilesDir(null)
        return "Almacenamiento de la app\n${dir?.absolutePath ?: "(desconocido)"}"
    }

    // ============ Custom SAF storage ============

    /**
     * Lists all .mp4 files in the SAF tree identified by [treeUri].
     * Returns a list of (uri, name, size, modified).
     */
    data class SafVideo(
        val uri: Uri,
        val name: String,
        val size: Long,
        val modified: Long
    )

    fun listSafVideos(ctx: Context, treeUri: Uri): List<SafVideo> {
        val tree = DocumentFile.fromTreeUri(ctx, treeUri) ?: return emptyList()
        return tree.listFiles()
            .filter { it.isFile && it.name?.endsWith(".mp4", ignoreCase = true) == true }
            .sortedByDescending { it.lastModified() }
            .map {
                SafVideo(it.uri, it.name ?: "?", it.length(), it.lastModified())
            }
    }

    /** Delete a single SAF file by uri. Returns true on success. */
    fun deleteSafFile(ctx: Context, uri: Uri): Boolean {
        return try {
            val docFile = DocumentFile.fromSingleUri(ctx, uri)
            docFile?.delete() ?: false
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Copy a video file (File) produced by CameraX into a SAF tree.
     * Used when the user picks a custom storage location.
     */
    fun copyFileToSaf(ctx: Context, source: File, treeUri: Uri, displayName: String): Uri? {
        return try {
            val tree = DocumentFile.fromTreeUri(ctx, treeUri) ?: return null
            val target = tree.createFile("video/mp4", displayName) ?: return null
            ctx.contentResolver.openOutputStream(target.uri)?.use { out ->
                FileInputStream(source).use { input ->
                    input.copyTo(out)
                }
            }
            // Remove local file after successful copy
            source.delete()
            target.uri
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Move (or copy if rename fails) [source] file into the SAF tree [treeUri] and rename it
     * to [displayName]. Returns the resulting Uri, or null on failure.
     *
     * If no [treeUri] is provided, the file stays where it is and is returned as-is.
     */
    fun moveToCustomStorage(ctx: Context, source: File, treeUri: Uri?, displayName: String): Uri? {
        if (treeUri == null) return Uri.fromFile(source)
        return try {
            val tree = DocumentFile.fromTreeUri(ctx, treeUri) ?: return null
            // If a file with the same name already exists, delete it first
            tree.findFile(displayName)?.delete()
            val target = tree.createFile("video/mp4", displayName) ?: return null
            ctx.contentResolver.openOutputStream(target.uri)?.use { out ->
                FileInputStream(source).use { input ->
                    input.copyTo(out)
                }
            }
            // Remove local file after successful copy
            source.delete()
            target.uri
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Lists all video files, looking in either the custom SAF location (if set) or the
     * default app-specific storage. Used by TripsScreen.
     */
    fun listAllVideoFiles(ctx: Context, customTreeUri: Uri?): List<SafVideo> {
        return if (customTreeUri != null) {
            listSafVideos(ctx, customTreeUri)
        } else {
            videosDir(ctx).listFiles()
                ?.filter { it.isFile && it.name.endsWith(".mp4", ignoreCase = true) }
                ?.sortedByDescending { it.lastModified() }
                ?.map {
                    SafVideo(Uri.fromFile(it), it.name, it.length(), it.lastModified())
                } ?: emptyList()
        }
    }
}

