package com.codingdrama.vlmwebrtc

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.syncrobotic.webrtc.config.MediaConfig
import com.syncrobotic.webrtc.session.SessionState
import com.syncrobotic.webrtc.session.WebRTCSession
import com.syncrobotic.webrtc.signaling.HttpSignalingAdapter
import com.syncrobotic.webrtc.ui.CameraPreview
import com.syncrobotic.webrtc.ui.VideoRenderer
import kotlinx.coroutines.launch

// ─────────────────────────────────────────────────────────
@Composable
fun MultiViewScreen() {
    var gridSize by remember { mutableStateOf(2) }

    Column(
        modifier = Modifier
            .background(AppColors.Background)
            .fillMaxSize()
            .safeContentPadding()
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Multi-View",
                color = AppColors.TextPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.weight(1f))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = gridSize == 2,
                    onClick = { gridSize = 2 },
                    label = { Text("1\u00d72", fontSize = 11.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = AppColors.Blue,
                        selectedLabelColor = Color.White,
                        containerColor = AppColors.Card,
                        labelColor = AppColors.TextMuted
                    )
                )
                FilterChip(
                    selected = gridSize == 4,
                    onClick = { gridSize = 4 },
                    label = { Text("2\u00d72", fontSize = 11.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = AppColors.Blue,
                        selectedLabelColor = Color.White,
                        containerColor = AppColors.Card,
                        labelColor = AppColors.TextMuted
                    )
                )
            }
        }

        // Grid
        if (gridSize == 2) {
            Column(Modifier.weight(1f).fillMaxWidth()) {
                RendererCell(
                    index = 0,
                    modifier = Modifier.weight(1f).fillMaxWidth()
                )
                Box(Modifier.fillMaxWidth().height(4.dp).background(AppColors.Background))
                RendererCell(
                    index = 1,
                    modifier = Modifier.weight(1f).fillMaxWidth()
                )
            }
        } else {
            Column(Modifier.weight(1f).fillMaxWidth()) {
                Row(Modifier.weight(1f).fillMaxWidth()) {
                    RendererCell(
                        index = 0,
                        modifier = Modifier.weight(1f).fillMaxHeight()
                    )
                    Box(Modifier.width(4.dp).fillMaxHeight().background(AppColors.Background))
                    RendererCell(
                        index = 1,
                        modifier = Modifier.weight(1f).fillMaxHeight()
                    )
                }
                Box(Modifier.fillMaxWidth().height(4.dp).background(AppColors.Background))
                Row(Modifier.weight(1f).fillMaxWidth()) {
                    RendererCell(
                        index = 2,
                        modifier = Modifier.weight(1f).fillMaxHeight()
                    )
                    Box(Modifier.width(4.dp).fillMaxHeight().background(AppColors.Background))
                    RendererCell(
                        index = 3,
                        modifier = Modifier.weight(1f).fillMaxHeight()
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────
@Composable
private fun RendererCell(index: Int, modifier: Modifier = Modifier) {
    // Allowed configs for multi-view (no need for audio-only modes)
    val allowedConfigs = listOf(MediaConfigOption.RECEIVE_VIDEO, MediaConfigOption.VIDEO_CALL)

    var urlInput by remember {
        mutableStateOf("http://10.8.100.229:8889/stream-${index + 1}/whep")
    }
    var selectedConfig by remember { mutableStateOf(MediaConfigOption.RECEIVE_VIDEO) }
    var activeSession by remember { mutableStateOf<WebRTCSession?>(null) }
    var sessionState by remember { mutableStateOf<SessionState>(SessionState.Idle) }

    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(activeSession) {
        val s = activeSession
        if (s != null) {
            coroutineScope.launch { s.state.collect { sessionState = it } }
        } else {
            sessionState = SessionState.Idle
        }
    }

    DisposableEffect(Unit) { onDispose { activeSession?.close() } }

    val isConnected = activeSession != null
    val stateDot = when (sessionState) {
        is SessionState.Connected -> AppColors.Green
        is SessionState.Connecting -> AppColors.Orange
        is SessionState.Reconnecting -> AppColors.Yellow
        is SessionState.Error -> AppColors.Red
        else -> AppColors.TextMuted
    }

    Box(
        modifier = modifier
            .background(AppColors.Card)
            .clip(RoundedCornerShape(0.dp))
    ) {
        if (isConnected && activeSession != null) {
            // Media area
            when (selectedConfig) {
                MediaConfigOption.RECEIVE_VIDEO -> {
                    VideoRenderer(
                        session = activeSession!!,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                MediaConfigOption.VIDEO_CALL -> {
                    VideoRenderer(
                        session = activeSession!!,
                        modifier = Modifier.fillMaxSize()
                    )
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(6.dp)
                            .size(80.dp, 60.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .border(1.dp, AppColors.Blue, RoundedCornerShape(6.dp))
                    ) {
                        CameraPreview(
                            session = activeSession!!,
                            modifier = Modifier.fillMaxSize(),
                            mirror = true
                        )
                    }
                }
                else -> {}
            }

            // State + disconnect overlay (top bar)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopStart)
                    .background(AppColors.Background.copy(alpha = 0.7f))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Box(Modifier.size(6.dp).background(stateDot, CircleShape))
                Text(
                    text = "CAM ${index + 1}",
                    color = AppColors.TextMuted,
                    fontSize = 10.sp,
                    modifier = Modifier.weight(1f)
                )
                TextButton(
                    onClick = { activeSession?.close(); activeSession = null },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text("\u2715", color = AppColors.Red, fontSize = 12.sp)
                }
            }

        } else {
            // Config + connect
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    "CAM ${index + 1}",
                    color = AppColors.TextMuted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    value = urlInput,
                    onValueChange = { urlInput = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("URL", fontSize = 10.sp) },
                    singleLine = true,
                    textStyle = androidx.compose.ui.text.TextStyle(
                        fontSize = 10.sp,
                        color = AppColors.TextPrimary
                    ),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = AppColors.TextPrimary,
                        unfocusedTextColor = AppColors.TextPrimary,
                        focusedBorderColor = AppColors.Blue,
                        unfocusedBorderColor = AppColors.CardBorder,
                        cursorColor = AppColors.Blue
                    )
                )
                Spacer(Modifier.height(6.dp))
                // Config chips
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    allowedConfigs.forEach { opt ->
                        FilterChip(
                            selected = selectedConfig == opt,
                            onClick = { selectedConfig = opt },
                            label = { Text(opt.label, fontSize = 9.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = AppColors.Blue,
                                selectedLabelColor = Color.White,
                                containerColor = AppColors.Background,
                                labelColor = AppColors.TextMuted
                            )
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
                Button(
                    onClick = {
                        val session = WebRTCSession(
                            signaling = HttpSignalingAdapter(url = urlInput.trim()),
                            mediaConfig = selectedConfig.config
                        )
                        activeSession = session
                        // Do NOT call session.connect() here.
                        // VideoRenderer's LaunchedEffect sets onClientReady first, then calls connect().
                        // Calling connect() here races with onClientReady setup → video sink never set → black screen.
                    },
                    modifier = Modifier.fillMaxWidth().height(36.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AppColors.Blue),
                    shape = RoundedCornerShape(18.dp),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Text("Connect", fontSize = 12.sp)
                }
            }
        }
    }
}
