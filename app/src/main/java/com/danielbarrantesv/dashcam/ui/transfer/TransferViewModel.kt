package com.danielbarrantesv.dashcam.ui.transfer

import android.content.Context
import androidx.lifecycle.ViewModel
import com.danielbarrantesv.dashcam.service.HttpServerService
import com.danielbarrantesv.dashcam.service.ServerState
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class TransferViewModel @Inject constructor(
    @ApplicationContext private val ctx: Context
) : ViewModel() {

    val state: StateFlow<ServerState> = HttpServerService.state

    fun startServer() {
        HttpServerService.start(ctx)
    }

    fun stopServer() {
        HttpServerService.stop(ctx)
    }
}
