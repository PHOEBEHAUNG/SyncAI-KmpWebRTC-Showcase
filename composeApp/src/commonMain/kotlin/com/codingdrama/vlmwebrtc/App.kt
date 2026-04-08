package com.codingdrama.vlmwebrtc

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import com.syncrobotic.webrtc.WebRTCStats
import com.syncrobotic.webrtc.audio.AudioPushPlayer
import com.syncrobotic.webrtc.audio.AudioPushState
import com.syncrobotic.webrtc.config.MediaConfig
import com.syncrobotic.webrtc.session.SessionState
import com.syncrobotic.webrtc.session.WebRTCSession
import com.syncrobotic.webrtc.signaling.HttpSignalingAdapter
import com.syncrobotic.webrtc.ui.CameraPreview
import com.syncrobotic.webrtc.ui.PlayerEvent
import com.syncrobotic.webrtc.ui.PlayerState
import com.syncrobotic.webrtc.ui.StreamInfo
import com.syncrobotic.webrtc.ui.VideoRenderer
import kotlin.math.round
import kotlinx.coroutines.launch
// ========== Color Theme ==========
internal object AppColors {
    val Background = Color(0xFF0D1B2A)
    val Card = Color(0xFF1B2838)
    val CardBorder = Color(0xFF2D4A5E)
    val Blue = Color(0xFF4A9EFF)
    val Green = Color(0xFF4CAF50)
    val Red = Color(0xFFE53935)
    val Orange = Color(0xFFFF9800)
    val Yellow = Color(0xFFFFD600)
    val TextPrimary = Color(0xFFFFFFFF)
    val TextMuted = Color(0xFF607D8B)
}

// ========== MediaConfig Options ==========
enum class MediaConfigOption(val label: String, val config: MediaConfig) {
    RECEIVE_VIDEO("Receive Video", MediaConfig.RECEIVE_VIDEO),
    SEND_VIDEO("Send Video", MediaConfig.SEND_VIDEO),
    SEND_AUDIO("Send Audio", MediaConfig.SEND_AUDIO),
    BIDIRECTIONAL("Intercom", MediaConfig.BIDIRECTIONAL_AUDIO),
    VIDEO_CALL("Video Call", MediaConfig.VIDEO_CALL)
}

// ========== App Tabs ==========
internal enum class AppTab(val label: String, val icon: String) {
    SINGLE("Single", "\uD83D\uDCE1"),
    DATA_CHANNEL("DataChannel", "\uD83D\uDCAC"),
    MULTI_VIEW("Multi-View", "\uD83D\uDCFA"),
    DUAL("Dual", "\uD83D\uDD00")
}

// ========== Entry Point ==========
@Composable
fun App() {
    var selectedTab by remember { mutableStateOf(AppTab.SINGLE) }
    Column(
        modifier = Modifier
            .background(AppColors.Background)
            .fillMaxSize()
    ) {
        Box(modifier = Modifier.weight(1f)) {
            when (selectedTab) {
                AppTab.SINGLE -> WebRTCTestScreen()
                AppTab.DATA_CHANNEL -> DataChannelScreen()
                AppTab.MULTI_VIEW -> MultiViewScreen()
                AppTab.DUAL -> DualSessionScreen()
            }
        }
        NavigationBar(
            containerColor = AppColors.Card,
            tonalElevation = 0.dp
        ) {
            AppTab.entries.forEach { tab ->
                NavigationBarItem(
                    selected = selectedTab == tab,
                    onClick = { selectedTab = tab },
                    icon = { Text(tab.icon, fontSize = 16.sp) },
                    label = { Text(tab.label, fontSize = 10.sp) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = AppColors.Blue,
                        selectedTextColor = AppColors.Blue,
                        unselectedIconColor = AppColors.TextMuted,
                        unselectedTextColor = AppColors.TextMuted,
                        indicatorColor = AppColors.Blue.copy(alpha = 0.15f)
                    )
                )
            }
        }
    }
}

// ========== Main Screen ==========
@Composable
fun WebRTCTestScreen() {
    var urlInput by remember {
        mutableStateOf("http://10.8.100.229:8090/api/v1/devices/iot-camera/offer")
    }
    var selectedConfig by remember { mutableStateOf(MediaConfigOption.RECEIVE_VIDEO) }
    var activeSession by remember { mutableStateOf<WebRTCSession?>(null) }
    var sessionState by remember { mutableStateOf<SessionState>(SessionState.Idle) }
    var stats by remember { mutableStateOf<WebRTCStats?>(null) }
    var isMuted by remember { mutableStateOf(false) }
    var isVideoEnabled by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var streamInfo by remember { mutableStateOf<StreamInfo?>(null) }

    // Collect session state + stats reactively
    val coroutineScope = rememberCoroutineScope()
    LaunchedEffect(activeSession) {
        val session = activeSession
        if (session != null) {
            coroutineScope.launch { session.state.collect { sessionState = it } }
            coroutineScope.launch { session.stats.collect { stats = it } }
        } else {
            sessionState = SessionState.Idle
            stats = null
            streamInfo = null
        }
    }

    val isSessionActive = activeSession != null

    DisposableEffect(Unit) {
        onDispose { activeSession?.close() }
    }

    Column(
        modifier = Modifier
            .background(AppColors.Background)
            .fillMaxSize()
            .safeContentPadding()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(12.dp))

        // ── Title ──────────────────────────────────────────────
        Text(
            text = "SyncAI WebRTC Test",
            color = AppColors.TextPrimary,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(Modifier.height(12.dp))

        // ── Config Panel (hidden when session active) ───────────
        if (!isSessionActive) {
            ConfigPanel(
                urlInput = urlInput,
                selectedConfig = selectedConfig,
                onUrlChange = { urlInput = it },
                onConfigChange = {
                    selectedConfig = it
                    errorMessage = null
                }
            )
        } else {
            // Active session summary
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .background(AppColors.Card, RoundedCornerShape(8.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = selectedConfig.label,
                    color = AppColors.Blue,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "•",
                    color = AppColors.TextMuted,
                    fontSize = 12.sp
                )
                Text(
                    text = urlInput,
                    color = AppColors.TextMuted,
                    fontSize = 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        Spacer(Modifier.height(10.dp))

        // ── Session State ───────────────────────────────────────
        SessionStateBar(state = sessionState, streamInfo = streamInfo)

        Spacer(Modifier.height(10.dp))

        // ── Media Area ──────────────────────────────────────────
        MediaArea(
            config = selectedConfig,
            session = activeSession,
            isSessionActive = isSessionActive,
            onStateChange = { playerState ->
                // sync PlayerState → SessionState-like for video sessions
            },
            onStreamInfo = { streamInfo = it }
        )

        Spacer(Modifier.height(12.dp))

        // ── Controls ────────────────────────────────────────────
        ControlsPanel(
            isSessionActive = isSessionActive,
            config = selectedConfig,
            sessionState = sessionState,
            isMuted = isMuted,
            isVideoEnabled = isVideoEnabled,
            errorMessage = errorMessage,
            onConnect = {
                errorMessage = null
                isMuted = false
                isVideoEnabled = true
                connectWithPermissions(
                    config = selectedConfig,
                    urlInput = urlInput.trim(),
                    onSuccess = { session ->
                        activeSession = session
                        // Audio-only modes need explicit connect (composables don't auto-connect)
                        if (!selectedConfig.config.receiveVideo && !selectedConfig.config.sendVideo) {
                            coroutineScope.launch { session.connect() }
                        }
                    },
                    onError = { msg -> errorMessage = msg }
                )
            },
            onDisconnect = {
                activeSession?.close()
                activeSession = null
                isMuted = false
                isVideoEnabled = true
                errorMessage = null
            },
            onMuteToggle = {
                isMuted = !isMuted
                activeSession?.setMuted(isMuted)
            },
            onVideoToggle = {
                isVideoEnabled = !isVideoEnabled
                activeSession?.setVideoEnabled(isVideoEnabled)
            },
            onSwitchCamera = { activeSession?.switchCamera() }
        )

        // ── Stats (when connected) ──────────────────────────────
        if (isSessionActive && stats != null) {
            Spacer(Modifier.height(12.dp))
            StatsRow(stats = stats!!)
        }

        Spacer(Modifier.height(24.dp))
    }
}

// ========== Config Panel ==========
@Composable
private fun ConfigPanel(
    urlInput: String,
    selectedConfig: MediaConfigOption,
    onUrlChange: (String) -> Unit,
    onConfigChange: (MediaConfigOption) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .background(AppColors.Card, RoundedCornerShape(12.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "CONFIGURATION",
            color = AppColors.TextMuted,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 1.sp
        )

        // URL input
        OutlinedTextField(
            value = urlInput,
            onValueChange = onUrlChange,
            label = { Text("Endpoint URL", fontSize = 12.sp) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = AppColors.TextPrimary,
                unfocusedTextColor = AppColors.TextPrimary,
                focusedBorderColor = AppColors.Blue,
                unfocusedBorderColor = AppColors.CardBorder,
                focusedLabelColor = AppColors.Blue,
                unfocusedLabelColor = AppColors.TextMuted,
                cursorColor = AppColors.Blue
            )
        )

        // MediaConfig chips
        Text(
            text = "Mode",
            color = AppColors.TextMuted,
            fontSize = 11.sp
        )
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            MediaConfigOption.entries.forEach { option ->
                val isSelected = option == selectedConfig
                FilterChip(
                    selected = isSelected,
                    onClick = { onConfigChange(option) },
                    label = { Text(option.label, fontSize = 12.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = AppColors.Blue,
                        selectedLabelColor = Color.White,
                        containerColor = AppColors.Background,
                        labelColor = AppColors.TextMuted
                    )
                )
            }
        }
    }
}

// ========== Session State Bar ==========
@Composable
private fun SessionStateBar(state: SessionState, streamInfo: StreamInfo?) {
    val (label, color) = when (state) {
        is SessionState.Idle -> "Idle" to AppColors.TextMuted
        is SessionState.Connecting -> "Connecting..." to AppColors.Orange
        is SessionState.Connected -> "Connected" to AppColors.Green
        is SessionState.Reconnecting -> "Reconnecting (${state.attempt}/${state.maxAttempts})" to AppColors.Yellow
        is SessionState.Error -> "Error: ${state.message}" to AppColors.Red
        is SessionState.Closed -> "Closed" to AppColors.TextMuted
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // State indicator dot
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(color, CircleShape)
        )
        Text(text = label, color = color, fontSize = 13.sp, fontWeight = FontWeight.Medium)

        Spacer(Modifier.weight(1f))

        // Stream info when playing
        streamInfo?.let {
            Text(
                text = "${it.height}p · ${it.fps.toInt()}fps",
                color = AppColors.TextMuted,
                fontSize = 11.sp
            )
        }
    }
}

// ========== Media Area ==========
@Composable
private fun MediaArea(
    config: MediaConfigOption,
    session: WebRTCSession?,
    isSessionActive: Boolean,
    onStateChange: (PlayerState) -> Unit,
    onStreamInfo: (StreamInfo) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .aspectRatio(16f / 9f)
            .clip(RoundedCornerShape(12.dp))
            .background(AppColors.Card)
            .border(1.dp, AppColors.CardBorder.copy(alpha = 0.4f), RoundedCornerShape(12.dp)),
        contentAlignment = Alignment.Center
    ) {
        if (!isSessionActive || session == null) {
            // Placeholder
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val icon = when (config) {
                    MediaConfigOption.RECEIVE_VIDEO -> "\uD83D\uDCFA"
                    MediaConfigOption.SEND_VIDEO -> "\uD83D\uDCF9"
                    MediaConfigOption.VIDEO_CALL -> "\uD83D\uDCF1"
                    MediaConfigOption.SEND_AUDIO -> "\uD83C\uDF99"
                    MediaConfigOption.BIDIRECTIONAL -> "\uD83C\uDFA7"
                }
                Text(text = icon, fontSize = 40.sp)
                Text(
                    text = config.label,
                    color = AppColors.TextMuted,
                    fontSize = 14.sp
                )
                Text(
                    text = "Enter URL and tap Connect",
                    color = AppColors.TextMuted.copy(alpha = 0.6f),
                    fontSize = 11.sp
                )
            }
        } else {
            when (config) {
                MediaConfigOption.RECEIVE_VIDEO -> {
                    VideoRenderer(
                        session = session,
                        modifier = Modifier.fillMaxSize(),
                        onStateChange = onStateChange,
                        onEvent = { event ->
                            if (event is PlayerEvent.StreamInfoReceived) onStreamInfo(event.info)
                        }
                    )
                }
                MediaConfigOption.SEND_VIDEO -> {
                    CameraPreview(
                        session = session,
                        modifier = Modifier.fillMaxSize(),
                        mirror = true
                    )
                }
                MediaConfigOption.VIDEO_CALL -> {
                    // Remote video full-screen
                    VideoRenderer(
                        session = session,
                        modifier = Modifier.fillMaxSize(),
                        onStateChange = onStateChange,
                        onEvent = { event ->
                            if (event is PlayerEvent.StreamInfoReceived) onStreamInfo(event.info)
                        }
                    )
                    // Local camera PiP — bottom-right overlay
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(8.dp)
                            .size(120.dp, 90.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .border(1.dp, AppColors.Blue, RoundedCornerShape(8.dp))
                    ) {
                        CameraPreview(
                            session = session,
                            modifier = Modifier.fillMaxSize(),
                            mirror = true
                        )
                    }
                }
                MediaConfigOption.SEND_AUDIO, MediaConfigOption.BIDIRECTIONAL -> {
                    AudioOnlyArea(session = session, config = config)
                }
            }
        }
    }
}

// ========== Audio-Only Area ==========
@Composable
private fun AudioOnlyArea(session: WebRTCSession, config: MediaConfigOption) {
    var pushState by remember { mutableStateOf<AudioPushState>(AudioPushState.Idle) }

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        val icon = if (config == MediaConfigOption.BIDIRECTIONAL) "\uD83C\uDFA7" else "\uD83C\uDF99"
        Text(text = icon, fontSize = 40.sp)

        Spacer(Modifier.height(8.dp))

        val stateLabel = when (pushState) {
            is AudioPushState.Idle -> "Ready"
            is AudioPushState.Connecting -> "Connecting..."
            is AudioPushState.Streaming -> "Streaming"
            is AudioPushState.Muted -> "Muted"
            is AudioPushState.Error -> "Error"
            is AudioPushState.Disconnected -> "Disconnected"
            else -> "Unknown"
        }
        Text(
            text = stateLabel,
            color = when (pushState) {
                is AudioPushState.Streaming -> AppColors.Green
                is AudioPushState.Error -> AppColors.Red
                is AudioPushState.Muted -> AppColors.Orange
                else -> AppColors.TextMuted
            },
            fontSize = 14.sp
        )

        // AudioPushPlayer composable (auto-starts, manages the mic)
        AudioPushPlayer(
            session = session,
            autoStart = true,
            onStateChange = { pushState = it }
        )
    }
}

// ========== Controls Panel ==========
@Composable
private fun ControlsPanel(
    isSessionActive: Boolean,
    config: MediaConfigOption,
    sessionState: SessionState,
    isMuted: Boolean,
    isVideoEnabled: Boolean,
    errorMessage: String?,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onMuteToggle: () -> Unit,
    onVideoToggle: () -> Unit,
    onSwitchCamera: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Error message
        errorMessage?.let {
            Text(
                text = it,
                color = AppColors.Red,
                fontSize = 12.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(AppColors.Red.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            )
        }

        if (!isSessionActive) {
            // Connect button
            Button(
                onClick = onConnect,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AppColors.Blue),
                shape = RoundedCornerShape(24.dp)
            ) {
                Text("Connect", color = Color.White, fontWeight = FontWeight.Medium)
            }
        } else {
            // Disconnect + media controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Disconnect
                OutlinedButton(
                    onClick = onDisconnect,
                    modifier = Modifier.weight(1f).height(48.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = AppColors.Red),
                    border = androidx.compose.foundation.BorderStroke(1.dp, AppColors.Red),
                    shape = RoundedCornerShape(24.dp)
                ) {
                    Text("Disconnect", fontWeight = FontWeight.Medium)
                }

                // Mute toggle (audio modes)
                if (config.config.sendAudio) {
                    MediaControlButton(
                        onClick = onMuteToggle,
                        icon = if (isMuted) "\uD83D\uDD07" else "\uD83C\uDF99",
                        active = !isMuted,
                        activeColor = AppColors.Blue,
                        inactiveColor = AppColors.Orange
                    )
                }

                // Video toggle (video send modes)
                if (config.config.sendVideo) {
                    MediaControlButton(
                        onClick = onVideoToggle,
                        icon = if (isVideoEnabled) "\uD83D\uDCF9" else "\uD83D\uDEAB",
                        active = isVideoEnabled,
                        activeColor = AppColors.Blue,
                        inactiveColor = AppColors.TextMuted
                    )

                    // Camera switch
                    MediaControlButton(
                        onClick = onSwitchCamera,
                        icon = "\uD83D\uDD04",
                        active = true,
                        activeColor = AppColors.Card
                    )
                }
            }
        }
    }
}

@Composable
private fun MediaControlButton(
    onClick: () -> Unit,
    icon: String,
    active: Boolean,
    activeColor: Color,
    inactiveColor: Color = AppColors.TextMuted
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(48.dp)
            .background(if (active) activeColor else inactiveColor, CircleShape)
    ) {
        Text(text = icon, fontSize = 18.sp)
    }
}

// ========== Stats Row ==========
@Composable
private fun StatsRow(stats: WebRTCStats) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        StatCard(label = "BITRATE", value = stats.bitrateDisplay, modifier = Modifier.weight(1f))
        StatCard(label = "LATENCY", value = stats.latencyDisplay, modifier = Modifier.weight(1f))
        StatCard(
            label = "LOSS",
            value = "${round(stats.packetLossPercent * 10) / 10}%",
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun StatCard(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .background(AppColors.Card, RoundedCornerShape(8.dp))
            .padding(10.dp)
    ) {
        Text(text = label, color = AppColors.TextMuted, fontSize = 9.sp, letterSpacing = 1.sp)
        Text(text = value, color = AppColors.TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }
}

// ========== Permission-aware connect ==========
private fun connectWithPermissions(
    config: MediaConfigOption,
    urlInput: String,
    onSuccess: (WebRTCSession) -> Unit,
    onError: (String) -> Unit
) {
    val needsCam = config.config.sendVideo
    val needsMic = config.config.sendAudio

    fun doConnect() {
        val session = WebRTCSession(
            signaling = HttpSignalingAdapter(url = urlInput),
            mediaConfig = config.config
        )
        onSuccess(session)
    }

    fun requestMic() {
        if (!needsMic) { doConnect(); return }
        if (checkPermission(Permission.RECORD_AUDIO) == PermissionStatus.GRANTED) {
            doConnect(); return
        }
        requestPermission(Permission.RECORD_AUDIO) { result ->
            if (result.status == PermissionStatus.GRANTED) doConnect()
            else onError("Microphone permission denied")
        }
    }

    if (needsCam) {
        if (checkPermission(Permission.CAMERA) == PermissionStatus.GRANTED) {
            requestMic(); return
        }
        requestPermission(Permission.CAMERA) { result ->
            if (result.status == PermissionStatus.GRANTED) requestMic()
            else onError("Camera permission denied")
        }
    } else {
        requestMic()
    }
}

