package com.codingdrama.vlmwebrtc

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.codingdrama.vlmwebrtc.permission.*
import com.syncrobotic.webrtc.audio.AudioPushPlayer
import com.syncrobotic.webrtc.audio.AudioPushState
import com.syncrobotic.webrtc.config.MediaConfig
import com.syncrobotic.webrtc.session.SessionState
import com.syncrobotic.webrtc.session.WebRTCSession
import com.syncrobotic.webrtc.signaling.HttpSignalingAdapter
import com.syncrobotic.webrtc.ui.CameraPreview
import com.syncrobotic.webrtc.ui.VideoRenderer
import kotlinx.coroutines.launch

// ─────────────────────────────────────────────────────────
@Composable
fun DualSessionScreen() {
    Column(
        modifier = Modifier
            .background(AppColors.Background)
            .fillMaxSize()
            .safeContentPadding()
    ) {
        Spacer(Modifier.height(8.dp))
        Text(
            "Dual Session",
            color = AppColors.TextPrimary,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )
        Text(
            "Run two independent sessions simultaneously",
            color = AppColors.TextMuted,
            fontSize = 11.sp,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )
        Spacer(Modifier.height(8.dp))

        // Panel A: Video receiver (top half)
        SessionPanel(
            label = "Session A",
            defaultUrl = "http://10.8.100.229:8889/iot-camera/whep",
            allowedConfigs = listOf(
                MediaConfigOption.RECEIVE_VIDEO,
                MediaConfigOption.VIDEO_CALL
            ),
            modifier = Modifier.weight(1f).fillMaxWidth()
        )

        // Divider
        Box(Modifier.fillMaxWidth().height(4.dp).background(AppColors.Background))

        // Panel B: Audio sender / intercom (bottom half)
        SessionPanel(
            label = "Session B",
            defaultUrl = "http://10.8.100.229:8080/iot-mic/whip",
            allowedConfigs = listOf(
                MediaConfigOption.SEND_AUDIO,
                MediaConfigOption.BIDIRECTIONAL,
                MediaConfigOption.SEND_VIDEO
            ),
            modifier = Modifier.weight(1f).fillMaxWidth()
        )

        Spacer(Modifier.height(8.dp))
    }
}

// ─────────────────────────────────────────────────────────
@Composable
private fun SessionPanel(
    label: String,
    defaultUrl: String,
    allowedConfigs: List<MediaConfigOption>,
    modifier: Modifier = Modifier
) {
    var urlInput by remember { mutableStateOf(defaultUrl) }
    var selectedConfig by remember { mutableStateOf(allowedConfigs.first()) }
    var activeSession by remember { mutableStateOf<WebRTCSession?>(null) }
    var sessionState by remember { mutableStateOf<SessionState>(SessionState.Idle) }
    var isMuted by remember { mutableStateOf(false) }
    var isVideoEnabled by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val coroutineScope = rememberCoroutineScope()
    val isConnected = activeSession != null

    LaunchedEffect(activeSession) {
        val s = activeSession
        if (s != null) {
            coroutineScope.launch { s.state.collect { sessionState = it } }
        } else {
            sessionState = SessionState.Idle
        }
    }

    DisposableEffect(Unit) { onDispose { activeSession?.close() } }

    val stateColor = when (sessionState) {
        is SessionState.Connected -> AppColors.Green
        is SessionState.Connecting -> AppColors.Orange
        is SessionState.Reconnecting -> AppColors.Yellow
        is SessionState.Error -> AppColors.Red
        else -> AppColors.TextMuted
    }

    fun doConnect() {
        val session = WebRTCSession(
            signaling = HttpSignalingAdapter(url = urlInput.trim()),
            mediaConfig = selectedConfig.config
        )
        activeSession = session
        // Video modes: let VideoRenderer's LaunchedEffect set onClientReady then call connect().
        // Audio-only modes have no VideoRenderer, so connect() must be called explicitly.
        if (!selectedConfig.config.receiveVideo && !selectedConfig.config.sendVideo) {
            coroutineScope.launch { session.connect() }
        }
    }

    fun connect() {
        errorMessage = null
        val needsCam = selectedConfig.config.sendVideo
        val needsMic = selectedConfig.config.sendAudio

        fun requestMic() {
            if (!needsMic) { doConnect(); return }
            if (checkPermission(Permission.RECORD_AUDIO) == PermissionStatus.GRANTED) {
                doConnect(); return
            }
            requestPermission(Permission.RECORD_AUDIO) { result ->
                if (result.status == PermissionStatus.GRANTED) doConnect()
                else errorMessage = "Microphone permission denied"
            }
        }

        if (needsCam) {
            if (checkPermission(Permission.CAMERA) == PermissionStatus.GRANTED) {
                requestMic(); return
            }
            requestPermission(Permission.CAMERA) { result ->
                if (result.status == PermissionStatus.GRANTED) requestMic()
                else errorMessage = "Camera permission denied"
            }
        } else {
            requestMic()
        }
    }

    Column(
        modifier = modifier
            .background(AppColors.Card)
            .padding(10.dp)
    ) {
        // Panel header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(Modifier.size(8.dp).background(stateColor, CircleShape))
            Text(label, color = AppColors.Blue, fontSize = 12.sp, fontWeight = FontWeight.Medium)
            Text("\u2022", color = AppColors.TextMuted, fontSize = 12.sp)
            Text(
                selectedConfig.label,
                color = AppColors.TextMuted,
                fontSize = 11.sp,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = when (sessionState) {
                    is SessionState.Connected -> "Connected"
                    is SessionState.Connecting -> "Connecting..."
                    is SessionState.Reconnecting -> "Reconnecting"
                    is SessionState.Error -> "Error"
                    is SessionState.Closed -> "Closed"
                    else -> "Idle"
                },
                color = stateColor,
                fontSize = 11.sp
            )
        }

        Spacer(Modifier.height(6.dp))

        // Config (hidden when connected)
        if (!isConnected) {
            OutlinedTextField(
                value = urlInput, onValueChange = { urlInput = it },
                placeholder = { Text("Endpoint URL", fontSize = 11.sp) },
                modifier = Modifier.fillMaxWidth(), singleLine = true,
                textStyle = androidx.compose.ui.text.TextStyle(
                    fontSize = 11.sp,
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
            // Mode chips
            if (allowedConfigs.size > 1) {
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    allowedConfigs.forEach { opt ->
                        FilterChip(
                            selected = selectedConfig == opt,
                            onClick = { selectedConfig = opt },
                            label = { Text(opt.label, fontSize = 10.sp) },
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
            }
        } else {
            Text(
                text = urlInput,
                color = AppColors.TextMuted,
                fontSize = 10.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(6.dp))
        }

        // Error message
        errorMessage?.let {
            Text(
                it,
                color = AppColors.Red,
                fontSize = 11.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(AppColors.Red.copy(alpha = 0.1f), RoundedCornerShape(6.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            )
            Spacer(Modifier.height(6.dp))
        }

        // Media area
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(AppColors.Background),
            contentAlignment = Alignment.Center
        ) {
            if (!isConnected || activeSession == null) {
                Text(selectedConfig.label, color = AppColors.TextMuted, fontSize = 13.sp)
            } else {
                PanelMediaArea(session = activeSession!!, config = selectedConfig)
            }
        }

        Spacer(Modifier.height(6.dp))

        // Controls row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (!isConnected) {
                Button(
                    onClick = { connect() },
                    modifier = Modifier.fillMaxWidth().height(40.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AppColors.Blue),
                    shape = RoundedCornerShape(20.dp)
                ) { Text("Connect", fontSize = 13.sp) }
            } else {
                OutlinedButton(
                    onClick = {
                        activeSession?.close()
                        activeSession = null
                        isMuted = false
                        isVideoEnabled = true
                        errorMessage = null
                    },
                    modifier = Modifier.weight(1f).height(40.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = AppColors.Red),
                    border = androidx.compose.foundation.BorderStroke(1.dp, AppColors.Red),
                    shape = RoundedCornerShape(20.dp)
                ) { Text("Disconnect", fontSize = 13.sp) }

                if (selectedConfig.config.sendAudio) {
                    IconButton(
                        onClick = { isMuted = !isMuted; activeSession?.setMuted(isMuted) },
                        modifier = Modifier
                            .size(40.dp)
                            .background(
                                if (isMuted) AppColors.Orange else AppColors.Blue,
                                CircleShape
                            )
                    ) { Text(if (isMuted) "\uD83D\uDD07" else "\uD83C\uDF99", fontSize = 16.sp) }
                }

                if (selectedConfig.config.sendVideo) {
                    IconButton(
                        onClick = { isVideoEnabled = !isVideoEnabled; activeSession?.setVideoEnabled(isVideoEnabled) },
                        modifier = Modifier
                            .size(40.dp)
                            .background(
                                if (isVideoEnabled) AppColors.Blue else AppColors.TextMuted,
                                CircleShape
                            )
                    ) { Text(if (isVideoEnabled) "\uD83D\uDCF9" else "\uD83D\uDEAB", fontSize = 16.sp) }

                    IconButton(
                        onClick = { activeSession?.switchCamera() },
                        modifier = Modifier.size(40.dp).background(AppColors.Card, CircleShape)
                    ) { Text("\uD83D\uDD04", fontSize = 16.sp) }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────
@Composable
private fun PanelMediaArea(session: WebRTCSession, config: MediaConfigOption) {
    when (config) {
        MediaConfigOption.RECEIVE_VIDEO -> {
            VideoRenderer(session = session, modifier = Modifier.fillMaxSize())
        }
        MediaConfigOption.SEND_VIDEO -> {
            CameraPreview(session = session, modifier = Modifier.fillMaxSize(), mirror = true)
        }
        MediaConfigOption.VIDEO_CALL -> {
            Box(modifier = Modifier.fillMaxSize()) {
                VideoRenderer(session = session, modifier = Modifier.fillMaxSize())
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(6.dp)
                        .size(90.dp, 68.dp)
                        .clip(RoundedCornerShape(6.dp))
                ) {
                    CameraPreview(session = session, modifier = Modifier.fillMaxSize(), mirror = true)
                }
            }
        }
        MediaConfigOption.SEND_AUDIO, MediaConfigOption.BIDIRECTIONAL -> {
            var pushState by remember { mutableStateOf<AudioPushState>(AudioPushState.Idle) }
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    if (config == MediaConfigOption.BIDIRECTIONAL) "\uD83C\uDFA7" else "\uD83C\uDF99",
                    fontSize = 32.sp
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = when (pushState) {
                        is AudioPushState.Streaming -> "Streaming"
                        is AudioPushState.Connecting -> "Connecting..."
                        is AudioPushState.Muted -> "Muted"
                        is AudioPushState.Error -> "Error"
                        is AudioPushState.Disconnected -> "Disconnected"
                        else -> "Ready"
                    },
                    color = when (pushState) {
                        is AudioPushState.Streaming -> AppColors.Green
                        is AudioPushState.Error -> AppColors.Red
                        is AudioPushState.Muted -> AppColors.Orange
                        else -> AppColors.TextMuted
                    },
                    fontSize = 13.sp
                )
                AudioPushPlayer(session = session, autoStart = true, onStateChange = { pushState = it })
            }
        }
    }
}
