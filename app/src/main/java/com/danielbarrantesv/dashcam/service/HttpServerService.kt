package com.danielbarrantesv.dashcam.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.wifi.WifiManager
import android.os.IBinder
import android.util.Log
import androidx.core.app.ServiceCompat
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.net.NetworkInterface
import javax.inject.Inject

/**
 * Foreground service that runs the embedded HTTP server.
 * Used for in-car WiFi transfer of videos and reports.
 */
@AndroidEntryPoint
class HttpServerService : Service() {

    @Inject lateinit var notifHelper: NotificationHelper

    private var server: DashCamHttpServer? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startServer()
            ACTION_STOP -> stopServer()
        }
        return START_STICKY
    }

    private fun startServer() {
        if (server != null) return // already running
        try {
            val s = DashCamHttpServer(this, PORT)
            s.start(SOCKET_READ_TIMEOUT, false)
            server = s

            val host = getWifiIp()
            _state.value = ServerState(isRunning = true, host = host, port = PORT, url = "http://$host:$PORT")

            val notif = notifHelper.buildServerNotification("$host:$PORT")
            ServiceCompat.startForeground(
                this,
                NotificationHelper.NOTIF_SERVER_ID,
                notif,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to start HTTP server", t)
            _state.value = ServerState(error = t.message)
            stopSelf()
        }
    }

    private fun stopServer() {
        server?.stop()
        server = null
        _state.value = ServerState()
        notifHelper.cancelServer()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    /** Returns the IPv4 of the WiFi interface, or "localhost" if not on WiFi. */
    private fun getWifiIp(): String {
        return try {
            val interfaces = NetworkInterface.getNetworkInterfaces().toList()
            for (iface in interfaces) {
                if (!iface.isUp || iface.isLoopback) continue
                if (!iface.displayName.contains("wlan", ignoreCase = true)) continue
                val addrs = iface.inetAddresses.toList()
                for (addr in addrs) {
                    val ip = addr.hostAddress ?: continue
                    if (ip.contains(":")) continue // skip IPv6
                    return ip
                }
            }
            "localhost"
        } catch (_: Throwable) {
            "localhost"
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        server?.stop()
        server = null
    }

    companion object {
        private const val TAG = "HttpServerService"
        const val PORT = 8080
        const val SOCKET_READ_TIMEOUT = 10000

        const val ACTION_START = "com.danielbarrantesv.dashcam.HTTP_START"
        const val ACTION_STOP = "com.danielbarrantesv.dashcam.HTTP_STOP"

        private val _state = MutableStateFlow(ServerState())
        val state: StateFlow<ServerState> = _state.asStateFlow()

        fun start(ctx: Context) {
            val intent = Intent(ctx, HttpServerService::class.java).apply { action = ACTION_START }
            ctx.startService(intent)
        }

        fun stop(ctx: Context) {
            val intent = Intent(ctx, HttpServerService::class.java).apply { action = ACTION_STOP }
            ctx.startService(intent)
        }
    }
}

data class ServerState(
    val isRunning: Boolean = false,
    val host: String = "localhost",
    val port: Int = 8080,
    val url: String = "",
    val error: String? = null
)
