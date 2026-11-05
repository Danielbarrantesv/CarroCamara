package com.danielbarrantesv.dashcam.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit

object Formatters {

    private val isoUtc = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    private val dateFmt = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
    private val timeFmt = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    private val dateTimeFmt = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())

    fun date(ts: Long): String = dateFmt.format(Date(ts))
    fun time(ts: Long): String = timeFmt.format(Date(ts))
    fun dateTime(ts: Long): String = dateTimeFmt.format(Date(ts))
    fun isoUtc(ts: Long): String = isoUtc.format(Date(ts))

    fun duration(seconds: Long): String {
        val h = TimeUnit.SECONDS.toHours(seconds)
        val m = TimeUnit.SECONDS.toMinutes(seconds) - h * 60
        val s = seconds - h * 3600 - m * 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
    }

    fun distance(meters: Double): String =
        if (meters >= 1000) "%.2f km".format(meters / 1000.0) else "%.0f m".format(meters)

    fun speed(ms: Float, factor: Float = 3.6f): String =
        "%.0f".format(ms * factor)

    fun coordinates(lat: Double, lon: Double): String =
        "%.5f, %.5f".format(lat, lon)

    fun bearing(deg: Float): String {
        val dirs = arrayOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")
        val idx = ((deg + 22.5f) / 45f).toInt() and 7
        return dirs[idx]
    }
}
