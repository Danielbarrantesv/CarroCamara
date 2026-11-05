package com.danielbarrantesv.dashcam.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

object Permissions {

    val camera = Manifest.permission.CAMERA
    val recordAudio = Manifest.permission.RECORD_AUDIO
    val fineLocation = Manifest.permission.ACCESS_FINE_LOCATION
    val coarseLocation = Manifest.permission.ACCESS_COARSE_LOCATION
    val backgroundLocation = Manifest.permission.ACCESS_BACKGROUND_LOCATION
    val postNotifications = Manifest.permission.POST_NOTIFICATIONS

    fun isCameraGranted(ctx: Context): Boolean =
        ContextCompat.checkSelfPermission(ctx, camera) == PackageManager.PERMISSION_GRANTED

    fun isAudioGranted(ctx: Context): Boolean =
        ContextCompat.checkSelfPermission(ctx, recordAudio) == PackageManager.PERMISSION_GRANTED

    fun isLocationGranted(ctx: Context): Boolean =
        ContextCompat.checkSelfPermission(ctx, fineLocation) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(ctx, coarseLocation) == PackageManager.PERMISSION_GRANTED

    fun isBackgroundLocationGranted(ctx: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContextCompat.checkSelfPermission(ctx, backgroundLocation) == PackageManager.PERMISSION_GRANTED
        } else true

    fun isNotificationsGranted(ctx: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(ctx, postNotifications) == PackageManager.PERMISSION_GRANTED
        } else true

    /** All permissions strictly needed to start recording. */
    fun allCoreGranted(ctx: Context): Boolean =
        isCameraGranted(ctx) &&
        isLocationGranted(ctx) &&
        isNotificationsGranted(ctx)
}
