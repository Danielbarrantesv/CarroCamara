package com.danielbarrantesv.dashcam.ui.transfer

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.danielbarrantesv.dashcam.ui.theme.DashCamTypography
import com.danielbarrantesv.dashcam.ui.theme.Primary
import com.danielbarrantesv.dashcam.ui.theme.Success

@Composable
fun TransferScreen(
    viewModel: TransferViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val ctx = LocalContext.current

    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            "Transferencia WiFi",
            style = DashCamTypography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            "Inicia el servidor para reproducir o descargar los videos y reportes desde cualquier dispositivo en la misma red WiFi.",
            style = DashCamTypography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // Server status card
        Card(
            Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(44.dp).clip(RoundedCornerShape(10.dp))
                            .background(if (state.isRunning) Success else MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            if (state.isRunning) Icons.Filled.Wifi else Icons.Filled.WifiOff,
                            contentDescription = null,
                            tint = if (state.isRunning) Color.Black else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            if (state.isRunning) "Servidor activo" else "Servidor detenido",
                            style = DashCamTypography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.SemiBold
                        )
                        if (state.isRunning) {
                            Text(
                                state.url,
                                style = DashCamTypography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Spacer(Modifier.height(20.dp))

                if (state.isRunning) {
                    Button(
                        onClick = { viewModel.stopServer() },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = MaterialTheme.colorScheme.onSurface
                        )
                    ) { Text("Detener servidor", fontWeight = FontWeight.SemiBold) }
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = {
                            val i = Intent(Intent.ACTION_VIEW, Uri.parse(state.url))
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            ctx.startActivity(i)
                        },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Primary)
                    ) {
                        Icon(Icons.Filled.OpenInBrowser, contentDescription = null, tint = Color.Black, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Abrir en navegador", color = Color.Black, fontWeight = FontWeight.SemiBold)
                    }
                } else {
                    Button(
                        onClick = { viewModel.startServer() },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Primary)
                    ) {
                        Icon(Icons.Filled.Wifi, contentDescription = null, tint = Color.Black, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Iniciar servidor", color = Color.Black, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }

        // Connection help card
        Card(
            Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(Modifier.padding(20.dp)) {
                Text("Cómo usar", style = DashCamTypography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(12.dp))
                HelpItem("1", "Conecta este teléfono y tu otro dispositivo a la misma red WiFi.")
                HelpItem("2", "Inicia el servidor con el botón de arriba.")
                HelpItem("3", "Abre la URL mostrada en el navegador del otro dispositivo.")
                HelpItem("4", "Reproduce o descarga videos y reportes. Sin cable, sin MicroSD.")
            }
        }
    }
}

@Composable
private fun HelpItem(num: String, text: String) {
    Row(
        Modifier.padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(24.dp).clip(RoundedCornerShape(12.dp)).background(Primary),
            contentAlignment = Alignment.Center
        ) {
            Text(num, color = Color.Black, fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace, style = DashCamTypography.labelSmall)
        }
        Spacer(Modifier.width(12.dp))
        Text(text, style = DashCamTypography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
    }
}
