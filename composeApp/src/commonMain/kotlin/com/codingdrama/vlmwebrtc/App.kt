package com.codingdrama.vlmwebrtc

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.codingdrama.vlmwebrtc.permission.*
import com.syncrobotic.webrtc.WebRTCStats
import com.syncrobotic.webrtc.audio.AudioPushController
import com.syncrobotic.webrtc.audio.AudioPushState
import com.syncrobotic.webrtc.config.MediaConfig
import com.syncrobotic.webrtc.session.WebRTCSession
import com.syncrobotic.webrtc.signaling.HttpSignalingAdapter
import com.syncrobotic.webrtc.ui.PlayerEvent
import com.syncrobotic.webrtc.ui.PlayerState
import com.syncrobotic.webrtc.ui.StreamInfo
import com.syncrobotic.webrtc.audio.AudioPushPlayer
import com.syncrobotic.webrtc.ui.VideoRenderer
import kotlinx.coroutines.delay

// ========== Color Theme ==========
private object StreamingColors {
    val Background = Color(0xFF0D1B2A)
    val CardBackground = Color(0xFF1B2838)
    val CardBorder = Color(0xFF2D4A5E)
    val AccentBlue = Color(0xFF4A9EFF)
    val AccentGreen = Color(0xFF4CAF50)
    val AccentRed = Color(0xFFE53935)
    val AccentOrange = Color(0xFFFF9800)
    val TextPrimary = Color(0xFFFFFFFF)
    val TextSecondary = Color(0xFFB0BEC5)
    val TextMuted = Color(0xFF607D8B)
    val LogTimestamp = Color(0xFF4A9EFF)
    val LogText = Color(0xFF90A4AE)
}

// ========== Mock System Logs ==========
private data class LogEntry(val timestamp: String, val message: String, val isHighlighted: Boolean = false)

private val mockLogs = listOf(
    LogEntry("[14:22:01]", "System initialized. Waiting for user input...", true),
    LogEntry("[14:22:05]", "Checking hardware compatibility: OK.", true),
    LogEntry("[14:23:10]", "Awaiting peer connection...", false)
)

@Composable
fun App() {
    StreamingView()
}

@Composable
fun StreamingView() {
    var showVideo by remember { mutableStateOf(false) }
    var playerState by remember { mutableStateOf<PlayerState>(PlayerState.Idle) }
    var streamInfo by remember { mutableStateOf<StreamInfo?>(null) }
    var firstFrameTime by remember { mutableStateOf<Long?>(null) }
    var connectionStartTime by remember { mutableStateOf<Long?>(null) }

    // Audio push states
    var isCallActive by remember { mutableStateOf(false) }
    var callDurationSeconds by remember { mutableStateOf(0) }
    var audioPushState by remember { mutableStateOf<AudioPushState>(AudioPushState.Idle) }
    var audioErrorMessage by remember { mutableStateOf<String?>(null) }
    var permissionStatus by remember { mutableStateOf<PermissionStatus?>(null) }
    var audioStats by remember { mutableStateOf<WebRTCStats?>(null) }

    // Video session — receives iot-camera stream via SignalingProxy → MediaMTX
    val videoSession = remember {
        WebRTCSession(
            signaling = HttpSignalingAdapter(url = "http://10.8.100.229:8090/api/v1/devices/iot-camera/offer"),
            mediaConfig = MediaConfig.RECEIVE_VIDEO
        )
    }
    DisposableEffect(videoSession) { onDispose { videoSession.close() } }

    // Audio session — sends microphone audio directly to MediaMTX WHIP
    val audioSession = remember {
        WebRTCSession(
            signaling = HttpSignalingAdapter(url = "http://10.8.100.229:8889/mobile-audio/whip"),
            mediaConfig = MediaConfig.SEND_AUDIO
        )
    }
    DisposableEffect(audioSession) { onDispose { audioSession.close() } }

    // Determine overall status
    val isLive = showVideo && playerState is PlayerState.Playing

    Column(
        modifier = Modifier
            .background(StreamingColors.Background)
            .fillMaxSize()
            .safeContentPadding()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(modifier = Modifier.height(8.dp))

        // ========== Top Status Badge ==========
        StatusBadge(isLive = isLive)

        Spacer(modifier = Modifier.height(16.dp))

        // ========== Video Preview Section ==========
        VideoPreviewSection(
            showVideo = showVideo,
            playerState = playerState,
            streamInfo = streamInfo,
            session = videoSession,
            onStateChange = { playerState = it },
            onStreamInfo = { streamInfo = it },
            onFirstFrame = { firstFrameTime = it },
            onConnectionStart = { connectionStartTime = it }
        )

        Spacer(modifier = Modifier.height(16.dp))

        // ========== VIDEO STREAM Section ==========
        VideoStreamSection(
            isLive = isLive,
            showVideo = showVideo,
            playerState = playerState,
            onToggleVideo = {
                if (showVideo) {
                    streamInfo = null
                    firstFrameTime = null
                    connectionStartTime = null
                    playerState = PlayerState.Idle
                }
                showVideo = !showVideo
            }
        )

        Spacer(modifier = Modifier.height(16.dp))

        // ========== Voice Call Section ==========
        VoiceCallSection(
            isCallActive = isCallActive,
            callDurationSeconds = callDurationSeconds,
            audioPushState = audioPushState,
            audioStats = audioStats,
            audioErrorMessage = audioErrorMessage,
            session = audioSession,
            onCallStart = {
                val currentStatus = checkPermission(Permission.RECORD_AUDIO)
                if (currentStatus == PermissionStatus.GRANTED) {
                    isCallActive = true
                    callDurationSeconds = 0
                } else {
                    requestPermission(Permission.RECORD_AUDIO) { result ->
                        permissionStatus = result.status
                        if (result.status == PermissionStatus.GRANTED) {
                            isCallActive = true
                            callDurationSeconds = 0
                        } else {
                            audioErrorMessage = "Microphone permission denied"
                        }
                    }
                }
            },
            onCallEnd = {
                isCallActive = false
                callDurationSeconds = 0
                audioPushState = AudioPushState.Idle
                audioErrorMessage = null
                audioStats = null
            },
            onStateChange = { state ->
                audioPushState = state
                when (state) {
                    is AudioPushState.Error -> audioErrorMessage = state.message
                    is AudioPushState.Streaming -> audioErrorMessage = null
                    else -> {}
                }
            },
            onStatsUpdate = { audioStats = it },
            onDurationTick = { callDurationSeconds++ }
        )

        Spacer(modifier = Modifier.height(16.dp))

        // ========== System Logs Section ==========
        SystemLogsSection()

        Spacer(modifier = Modifier.height(16.dp))

        // Permission denied message
        if (permissionStatus == PermissionStatus.DENIED && !isCallActive) {
            Text(
                text = "Microphone permission is required for calls",
                style = MaterialTheme.typography.bodySmall,
                color = StreamingColors.AccentRed,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}

// ========== Status Badge Component ==========
@Composable
private fun StatusBadge(isLive: Boolean) {
    Box(
        modifier = Modifier
            .background(
                color = StreamingColors.AccentGreen,
                shape = RoundedCornerShape(12.dp)
            )
            .padding(horizontal = 16.dp, vertical = 6.dp)
    ) {
        Text(
            text = if (isLive) "Live" else "Ready",
            color = Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

// ========== Video Preview Section ==========
@Composable
private fun VideoPreviewSection(
    showVideo: Boolean,
    playerState: PlayerState,
    streamInfo: StreamInfo?,
    session: WebRTCSession,
    onStateChange: (PlayerState) -> Unit,
    onStreamInfo: (StreamInfo) -> Unit,
    onFirstFrame: (Long) -> Unit,
    onConnectionStart: (Long) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .aspectRatio(16f / 9f)
            .clip(RoundedCornerShape(12.dp))
            .background(StreamingColors.CardBackground)
            .border(1.dp, StreamingColors.CardBorder.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
    ) {
        if (showVideo) {
            LaunchedEffect(Unit) {
                onConnectionStart(currentTimeMillis())
            }

            VideoRenderer(
                session = session,
                modifier = Modifier.fillMaxSize(),
                onStateChange = onStateChange,
                onEvent = { event ->
                    when (event) {
                        is PlayerEvent.FirstFrameRendered -> onFirstFrame(event.timestampMs)
                        is PlayerEvent.StreamInfoReceived -> onStreamInfo(event.info)
                        else -> {}
                    }
                }
            )

            // FPS/RES Overlay (top-left)
            Row(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // FPS indicator with green dot
                Row(
                    modifier = Modifier
                        .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (playerState is PlayerState.Playing) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .background(StreamingColors.AccentGreen, CircleShape)
                        )
                    }
                    Text(
                        text = "FPS: ${streamInfo?.fps?.toInt() ?: 0}",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                // Resolution
                Box(
                    modifier = Modifier
                        .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "RES: ${streamInfo?.let { "${it.height}P" } ?: "N/A"}",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        } else {
            // Camera Off placeholder
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // FPS/RES overlay even when off
                Row(
                    modifier = Modifier
                        .align(Alignment.Start)
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "FPS: 0",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    Box(
                        modifier = Modifier
                            .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "RES: N/A",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                Spacer(modifier = Modifier.weight(1f))

                // Camera icon placeholder
                Text(
                    text = "\uD83D\uDCF7",  // Camera emoji as placeholder
                    fontSize = 48.sp,
                    color = StreamingColors.TextMuted
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Camera Off",
                    color = StreamingColors.TextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium
                )

                Text(
                    text = "Ready to establish stream",
                    color = StreamingColors.TextMuted,
                    fontSize = 14.sp
                )

                Spacer(modifier = Modifier.weight(1f))
            }
        }
    }
}

// ========== VIDEO STREAM Section ==========
@Composable
private fun VideoStreamSection(
    isLive: Boolean,
    showVideo: Boolean,
    playerState: PlayerState,
    onToggleVideo: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        // Section header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "VIDEO STREAM",
                color = StreamingColors.TextMuted,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 1.sp
            )

            Text(
                text = if (isLive) "LIVE" else "INACTIVE",
                color = if (isLive) StreamingColors.AccentGreen else StreamingColors.TextMuted,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Toggle button
        Button(
            onClick = onToggleVideo,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (showVideo) StreamingColors.AccentRed else StreamingColors.AccentBlue
            ),
            shape = RoundedCornerShape(24.dp)
        ) {
            Text(
                text = if (showVideo) "Turn Off" else "Establish Video Stream",
                color = Color.White,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

// ========== Voice Call Section ==========
@Composable
private fun VoiceCallSection(
    isCallActive: Boolean,
    callDurationSeconds: Int,
    audioPushState: AudioPushState,
    audioStats: WebRTCStats?,
    audioErrorMessage: String?,
    session: WebRTCSession,
    onCallStart: () -> Unit,
    onCallEnd: () -> Unit,
    onStateChange: (AudioPushState) -> Unit,
    onStatsUpdate: (WebRTCStats?) -> Unit,
    onDurationTick: () -> Unit
) {
    // Controller reference for active calls
    var controllerRef by remember { mutableStateOf<AudioPushController?>(null) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .background(StreamingColors.CardBackground, RoundedCornerShape(12.dp))
            .border(1.dp, StreamingColors.CardBorder.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
            .padding(16.dp)
    ) {
        // Header row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Voice Call",
                    color = StreamingColors.TextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "Audio-only communication test",
                    color = StreamingColors.TextMuted,
                    fontSize = 12.sp
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (isCallActive) {
                    Text(
                        text = formatDuration(callDurationSeconds),
                        color = StreamingColors.AccentGreen,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
                // Headphone icon
                Text(
                    text = "\uD83C\uDFA7",  // Headphone emoji
                    fontSize = 20.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Stats row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Bitrate
            Column(
                modifier = Modifier
                    .weight(1f)
                    .background(StreamingColors.Background, RoundedCornerShape(8.dp))
                    .padding(12.dp)
            ) {
                Text(
                    text = "BITRATE",
                    color = StreamingColors.TextMuted,
                    fontSize = 10.sp,
                    letterSpacing = 1.sp
                )
                Text(
                    text = audioStats?.bitrateDisplay ?: "128 kbps",
                    color = StreamingColors.TextPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            // Latency
            Column(
                modifier = Modifier
                    .weight(1f)
                    .background(StreamingColors.Background, RoundedCornerShape(8.dp))
                    .padding(12.dp)
            ) {
                Text(
                    text = "LATENCY",
                    color = StreamingColors.TextMuted,
                    fontSize = 10.sp,
                    letterSpacing = 1.sp
                )
                Text(
                    text = audioStats?.latencyDisplay ?: "24ms",
                    color = StreamingColors.AccentGreen,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Error message
        audioErrorMessage?.let { error ->
            Text(
                text = error,
                color = StreamingColors.AccentRed,
                fontSize = 12.sp,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }

        // Call controls
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isCallActive) {
                // Active call UI
                LaunchedEffect(isCallActive) {
                    while (isCallActive) {
                        delay(1000)
                        onDurationTick()
                    }
                }

                val controller = AudioPushPlayer(
                    session = session,
                    autoStart = true,
                    onStateChange = onStateChange
                )

                LaunchedEffect(controller) {
                    controllerRef = controller
                }

                // Update stats periodically
                LaunchedEffect(isCallActive) {
                    while (isCallActive) {
                        onStatsUpdate(controller.stats)
                        delay(1000)
                    }
                }

                // End Call button
                OutlinedButton(
                    onClick = {
                        controller.stop()
                        onCallEnd()
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = StreamingColors.AccentRed
                    ),
                    border = androidx.compose.foundation.BorderStroke(1.dp, StreamingColors.AccentRed),
                    shape = RoundedCornerShape(24.dp)
                ) {
                    Text("End Call", fontWeight = FontWeight.Medium)
                }

                // Mute button
                IconButton(
                    onClick = { controller.toggleMute() },
                    modifier = Modifier
                        .size(48.dp)
                        .background(
                            if (controller.isMuted) StreamingColors.AccentOrange else StreamingColors.AccentBlue,
                            CircleShape
                        )
                ) {
                    Text(
                        text = if (controller.isMuted) "\uD83D\uDD07" else "\uD83C\uDF99",
                        fontSize = 20.sp
                    )
                }
            } else {
                // Start Call button
                OutlinedButton(
                    onClick = onCallStart,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = StreamingColors.AccentGreen
                    ),
                    border = androidx.compose.foundation.BorderStroke(1.dp, StreamingColors.AccentGreen),
                    shape = RoundedCornerShape(24.dp)
                ) {
                    Text("Start Audio Call", fontWeight = FontWeight.Medium)
                }

                // Mute button (disabled when not in call)
                IconButton(
                    onClick = { },
                    enabled = false,
                    modifier = Modifier
                        .size(48.dp)
                        .background(StreamingColors.CardBorder, CircleShape)
                ) {
                    Text(
                        text = "\uD83D\uDD07",
                        fontSize = 20.sp
                    )
                }
            }
        }
    }
}

// ========== System Logs Section ==========
@Composable
private fun SystemLogsSection() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        // Section header
        Text(
            text = "SYSTEM LOGS",
            color = StreamingColors.TextMuted,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 1.sp
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Logs container
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(StreamingColors.CardBackground, RoundedCornerShape(12.dp))
                .border(1.dp, StreamingColors.CardBorder.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            mockLogs.forEach { log ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = log.timestamp,
                        color = if (log.isHighlighted) StreamingColors.LogTimestamp else StreamingColors.TextMuted,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = log.message,
                        color = if (log.isHighlighted) StreamingColors.LogText else StreamingColors.TextMuted.copy(alpha = 0.6f),
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
    }
}

/**
 * Format seconds to MM:SS or HH:MM:SS format.
 */
private fun formatDuration(seconds: Int): String {
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    val secs = seconds % 60

    return if (hours > 0) {
        "${hours.toString().padStart(2, '0')}:${minutes.toString().padStart(2, '0')}:${secs.toString().padStart(2, '0')}"
    } else {
        "${minutes.toString().padStart(2, '0')}:${secs.toString().padStart(2, '0')}"
    }
}

/**
 * Get current time in milliseconds.
 */
expect fun currentTimeMillis(): Long