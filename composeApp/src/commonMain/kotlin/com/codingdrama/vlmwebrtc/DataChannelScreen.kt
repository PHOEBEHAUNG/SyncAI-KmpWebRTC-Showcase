package com.codingdrama.vlmwebrtc

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.codingdrama.vlmwebrtc.permission.*
import com.syncrobotic.webrtc.config.MediaConfig
import com.syncrobotic.webrtc.datachannel.DataChannel
import com.syncrobotic.webrtc.datachannel.DataChannelConfig
import com.syncrobotic.webrtc.datachannel.DataChannelListenerAdapter
import com.syncrobotic.webrtc.datachannel.DataChannelState
import com.syncrobotic.webrtc.session.SessionState
import com.syncrobotic.webrtc.session.WebRTCSession
import com.syncrobotic.webrtc.signaling.HttpSignalingAdapter
import com.syncrobotic.webrtc.ui.VideoRenderer
import kotlinx.coroutines.launch

private data class DcMessage(
    val text: String,
    val isOutgoing: Boolean,
    val isBinary: Boolean = false
)

// ─────────────────────────────────────────────────────────
@Composable
fun DataChannelScreen() {
    // ── Config state ────────────────────────────────────
    var urlInput by remember { mutableStateOf("http://10.8.100.229:8080/dc-test/whip") }
    var ch1Name by remember { mutableStateOf("messages") }
    var ch1Reliable by remember { mutableStateOf(true) }
    var isReceiveMode by remember { mutableStateOf(false) } // true=WHEP(RECEIVE_VIDEO), false=WHIP(SEND_AUDIO)
    var showVideo by remember { mutableStateOf(false) }
    var showCh2 by remember { mutableStateOf(false) }
    var ch2Name by remember { mutableStateOf("telemetry") }
    var ch2Reliable by remember { mutableStateOf(false) }

    // ── Session & DC state ───────────────────────────────
    var activeSession by remember { mutableStateOf<WebRTCSession?>(null) }
    var sessionState by remember { mutableStateOf<SessionState>(SessionState.Idle) }
    var primaryDc by remember { mutableStateOf<DataChannel?>(null) }
    var secondaryDc by remember { mutableStateOf<DataChannel?>(null) }
    var dc1State by remember { mutableStateOf(DataChannelState.CLOSED) }
    var dc2State by remember { mutableStateOf(DataChannelState.CLOSED) }
    val messages = remember { mutableStateListOf<DcMessage>() }
    var textInput by remember { mutableStateOf("") }

    val coroutineScope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    LaunchedEffect(activeSession) {
        val s = activeSession
        if (s != null) {
            coroutineScope.launch {
                s.state.collect { state ->
                    sessionState = state
                    // createDataChannel() before connect() returns null (added to pending).
                    // doConnect() creates the actual DataChannel inside the session.
                    // Once Connected, call createDataChannel() again to get the real reference,
                    // then set listener (setListener replays current state, so OPEN is caught).
                    if (state is SessionState.Connected) {
                        if (primaryDc == null) {
                            val dcConfig = if (ch1Reliable) DataChannelConfig.reliable(ch1Name)
                                           else DataChannelConfig.unreliable(ch1Name)
                            s.createDataChannel(dcConfig)?.also { dc ->
                                dc.setListener(object : DataChannelListenerAdapter() {
                                    override fun onStateChanged(dcState: DataChannelState) { dc1State = dcState }
                                    override fun onMessage(message: String) {
                                        messages.add(DcMessage(message, isOutgoing = false))
                                    }
                                    override fun onBinaryMessage(data: ByteArray) {
                                        val hex = data.joinToString(" ") { it.toInt().and(0xFF).toString(16).uppercase().padStart(2, '0') }
                                        messages.add(DcMessage("[binary: $hex]", isOutgoing = false, isBinary = true))
                                    }
                                })
                                primaryDc = dc
                            }
                        }
                        if (showCh2 && secondaryDc == null) {
                            val dc2Config = if (ch2Reliable) DataChannelConfig.reliable(ch2Name)
                                            else DataChannelConfig.unreliable(ch2Name)
                            s.createDataChannel(dc2Config)?.also { dc2 ->
                                dc2.setListener(object : DataChannelListenerAdapter() {
                                    override fun onStateChanged(dcState: DataChannelState) { dc2State = dcState }
                                    override fun onMessage(message: String) {
                                        messages.add(DcMessage("[${ch2Name}] $message", isOutgoing = false))
                                    }
                                })
                                secondaryDc = dc2
                            }
                        }
                    }
                }
            }
        } else {
            sessionState = SessionState.Idle
            dc1State = DataChannelState.CLOSED
            dc2State = DataChannelState.CLOSED
        }
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }

    DisposableEffect(Unit) { onDispose { activeSession?.close() } }

    val isConnected = activeSession != null

    // ── Connect / Disconnect helpers ─────────────────────
    fun doConnect() {
        val mediaConfig = if (isReceiveMode) MediaConfig.RECEIVE_VIDEO else MediaConfig.SEND_AUDIO
        val session = WebRTCSession(
            signaling = HttpSignalingAdapter(url = urlInput.trim()),
            mediaConfig = mediaConfig
        )

        // Register DataChannel configs before connect so they're included in SDP negotiation.
        // createDataChannel() returns null at this stage (added to pending inside the session).
        // The actual DataChannel reference is retrieved in LaunchedEffect once Connected.
        val dc1Config = if (ch1Reliable) DataChannelConfig.reliable(ch1Name)
                        else DataChannelConfig.unreliable(ch1Name)
        session.createDataChannel(dc1Config)

        if (showCh2) {
            val dc2Config = if (ch2Reliable) DataChannelConfig.reliable(ch2Name)
                            else DataChannelConfig.unreliable(ch2Name)
            session.createDataChannel(dc2Config)
        }

        activeSession = session
        // When showVideo=true in WHEP mode, VideoRenderer's LaunchedEffect sets onClientReady
        // then calls connect() automatically — calling it explicitly here would race.
        if (!(isReceiveMode && showVideo)) {
            coroutineScope.launch { session.connect() }
        }
    }

    fun connect() {
        if (!isReceiveMode) {
            if (checkPermission(Permission.RECORD_AUDIO) == PermissionStatus.GRANTED) {
                doConnect()
            } else {
                requestPermission(Permission.RECORD_AUDIO) { result ->
                    if (result.status == PermissionStatus.GRANTED) doConnect()
                }
            }
        } else {
            doConnect()
        }
    }

    fun disconnect() {
        primaryDc?.close()
        secondaryDc?.close()
        activeSession?.close()
        activeSession = null
        primaryDc = null
        secondaryDc = null
        messages.clear()
    }

    // ── UI ───────────────────────────────────────────────
    Column(
        modifier = Modifier
            .background(AppColors.Background)
            .fillMaxSize()
            .safeContentPadding()
    ) {
        Spacer(Modifier.height(12.dp))
        Text(
            text = "DataChannel Test",
            color = AppColors.TextPrimary,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )
        Spacer(Modifier.height(8.dp))

        // Config panel (hidden when connected)
        if (!isConnected) {
            DcConfigPanel(
                urlInput = urlInput,
                ch1Name = ch1Name, ch1Reliable = ch1Reliable,
                isReceiveMode = isReceiveMode,
                showVideo = showVideo,
                showCh2 = showCh2, ch2Name = ch2Name, ch2Reliable = ch2Reliable,
                onUrlChange = { urlInput = it },
                onCh1NameChange = { ch1Name = it },
                onCh1ReliableChange = { ch1Reliable = it },
                onReceiveModeChange = { isReceiveMode = it },
                onShowVideoChange = { showVideo = it },
                onShowCh2Change = { showCh2 = it },
                onCh2NameChange = { ch2Name = it },
                onCh2ReliableChange = { ch2Reliable = it }
            )
        } else {
            // Active summary bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .background(AppColors.Card, RoundedCornerShape(8.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val stateColor = when (sessionState) {
                    is SessionState.Connected -> AppColors.Green
                    is SessionState.Connecting -> AppColors.Orange
                    is SessionState.Error -> AppColors.Red
                    else -> AppColors.TextMuted
                }
                Box(Modifier.size(8.dp).background(stateColor, CircleShape))
                Text(
                    text = when (sessionState) {
                        is SessionState.Connected -> "Connected"
                        is SessionState.Connecting -> "Connecting..."
                        is SessionState.Reconnecting -> "Reconnecting"
                        is SessionState.Error -> "Error"
                        else -> "Idle"
                    },
                    color = stateColor, fontSize = 12.sp
                )
                Spacer(Modifier.weight(1f))
                DcStateBadge(ch1Name, dc1State)
                if (showCh2) DcStateBadge(ch2Name, dc2State)
            }
        }

        Spacer(Modifier.height(8.dp))

        // Optional video renderer
        if (showVideo && isConnected && activeSession != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(AppColors.Card)
            ) {
                VideoRenderer(session = activeSession!!, modifier = Modifier.fillMaxSize())
            }
            Spacer(Modifier.height(8.dp))
        }

        // Message log
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .background(AppColors.Card, RoundedCornerShape(8.dp))
        ) {
            if (messages.isEmpty()) {
                Text(
                    text = if (isConnected) "Waiting for messages..." else "Connect to start",
                    color = AppColors.TextMuted,
                    fontSize = 13.sp,
                    modifier = Modifier.align(Alignment.Center)
                )
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(messages) { msg -> DcMessageBubble(msg) }
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        // Input row (only when connected)
        if (isConnected) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = textInput,
                        onValueChange = { textInput = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("Type a message...", fontSize = 12.sp) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = AppColors.TextPrimary,
                            unfocusedTextColor = AppColors.TextPrimary,
                            focusedBorderColor = AppColors.Blue,
                            unfocusedBorderColor = AppColors.CardBorder,
                            cursorColor = AppColors.Blue,
                            unfocusedLabelColor = AppColors.TextMuted
                        )
                    )
                    Button(
                        onClick = {
                            val msg = textInput.trim()
                            val dc = primaryDc
                            if (msg.isNotEmpty() && dc != null && dc.send(msg)) {
                                messages.add(DcMessage(msg, isOutgoing = true))
                                textInput = ""
                            }
                        },
                        enabled = dc1State == DataChannelState.OPEN && textInput.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(containerColor = AppColors.Blue),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                        Text("Send", fontSize = 13.sp)
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Binary send (C5-02)
                    OutlinedButton(
                        onClick = {
                            val binData = byteArrayOf(0x01, 0x02, 0x03, 0xFF.toByte())
                            primaryDc?.sendBinary(binData)
                            messages.add(DcMessage("[binary sent: 01 02 03 FF]", isOutgoing = true, isBinary = true))
                        },
                        enabled = dc1State == DataChannelState.OPEN,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = AppColors.Orange),
                        border = BorderStroke(1.dp, AppColors.Orange)
                    ) { Text("Binary", fontSize = 12.sp) }

                    // Rapid send ×100 (C5-05)
                    OutlinedButton(
                        onClick = {
                            val dc = primaryDc ?: return@OutlinedButton
                            coroutineScope.launch {
                                for (i in 1..100) dc.send("{\"seq\":$i}")
                                messages.add(DcMessage("Sent 100 messages (seq:1~100)", isOutgoing = true))
                            }
                        },
                        enabled = dc1State == DataChannelState.OPEN,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = AppColors.Yellow),
                        border = BorderStroke(1.dp, AppColors.Yellow)
                    ) { Text("\u00d7100", fontSize = 12.sp) }

                    // Clear log
                    OutlinedButton(
                        onClick = { messages.clear() },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = AppColors.TextMuted),
                        border = BorderStroke(1.dp, AppColors.CardBorder)
                    ) { Text("Clear", fontSize = 12.sp) }
                }
            }
            Spacer(Modifier.height(8.dp))
        }

        // Connect / Disconnect
        Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            if (!isConnected) {
                Button(
                    onClick = { connect() },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AppColors.Blue),
                    shape = RoundedCornerShape(24.dp)
                ) { Text("Connect", fontWeight = FontWeight.Medium) }
            } else {
                OutlinedButton(
                    onClick = { disconnect() },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = AppColors.Red),
                    border = BorderStroke(1.dp, AppColors.Red),
                    shape = RoundedCornerShape(24.dp)
                ) { Text("Disconnect", fontWeight = FontWeight.Medium) }
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

// ─────────────────────────────────────────────────────────
@Composable
private fun DcConfigPanel(
    urlInput: String,
    ch1Name: String, ch1Reliable: Boolean,
    isReceiveMode: Boolean,
    showVideo: Boolean,
    showCh2: Boolean, ch2Name: String, ch2Reliable: Boolean,
    onUrlChange: (String) -> Unit,
    onCh1NameChange: (String) -> Unit,
    onCh1ReliableChange: (Boolean) -> Unit,
    onReceiveModeChange: (Boolean) -> Unit,
    onShowVideoChange: (Boolean) -> Unit,
    onShowCh2Change: (Boolean) -> Unit,
    onCh2NameChange: (String) -> Unit,
    onCh2ReliableChange: (Boolean) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .background(AppColors.Card, RoundedCornerShape(12.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("CONFIGURATION", color = AppColors.TextMuted, fontSize = 11.sp, letterSpacing = 1.sp)

        OutlinedTextField(
            value = urlInput, onValueChange = onUrlChange,
            label = { Text("Endpoint URL", fontSize = 12.sp) },
            modifier = Modifier.fillMaxWidth(), singleLine = true,
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

        // Connection mode: WHEP / WHIP
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Mode:", color = AppColors.TextMuted, fontSize = 11.sp)
            FilterChip(
                selected = isReceiveMode,
                onClick = { onReceiveModeChange(true) },
                label = { Text("WHEP (Receive)", fontSize = 11.sp) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = AppColors.Blue,
                    selectedLabelColor = Color.White,
                    containerColor = AppColors.Background,
                    labelColor = AppColors.TextMuted
                )
            )
            FilterChip(
                selected = !isReceiveMode,
                onClick = { onReceiveModeChange(false) },
                label = { Text("WHIP (Send)", fontSize = 11.sp) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = AppColors.Blue,
                    selectedLabelColor = Color.White,
                    containerColor = AppColors.Background,
                    labelColor = AppColors.TextMuted
                )
            )
        }

        // Channel 1
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = ch1Name, onValueChange = onCh1NameChange,
                label = { Text("CH1 Name", fontSize = 11.sp) },
                modifier = Modifier.weight(1f), singleLine = true,
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
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Reliable", color = AppColors.TextMuted, fontSize = 10.sp)
                Switch(
                    checked = ch1Reliable, onCheckedChange = onCh1ReliableChange,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = AppColors.Blue
                    )
                )
            }
        }

        // Options row
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = showVideo,
                onClick = { onShowVideoChange(!showVideo) },
                label = { Text("Show Video", fontSize = 11.sp) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = AppColors.Blue.copy(alpha = 0.8f),
                    selectedLabelColor = Color.White,
                    containerColor = AppColors.Background,
                    labelColor = AppColors.TextMuted
                )
            )
            FilterChip(
                selected = showCh2,
                onClick = { onShowCh2Change(!showCh2) },
                label = { Text("+ CH2", fontSize = 11.sp) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = AppColors.Orange.copy(alpha = 0.8f),
                    selectedLabelColor = Color.White,
                    containerColor = AppColors.Background,
                    labelColor = AppColors.TextMuted
                )
            )
        }

        // Channel 2 (optional)
        if (showCh2) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = ch2Name, onValueChange = onCh2NameChange,
                    label = { Text("CH2 Name", fontSize = 11.sp) },
                    modifier = Modifier.weight(1f), singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = AppColors.TextPrimary,
                        unfocusedTextColor = AppColors.TextPrimary,
                        focusedBorderColor = AppColors.Orange,
                        unfocusedBorderColor = AppColors.CardBorder,
                        focusedLabelColor = AppColors.Orange,
                        unfocusedLabelColor = AppColors.TextMuted,
                        cursorColor = AppColors.Orange
                    )
                )
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Reliable", color = AppColors.TextMuted, fontSize = 10.sp)
                    Switch(
                        checked = ch2Reliable, onCheckedChange = onCh2ReliableChange,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = AppColors.Orange
                        )
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────
@Composable
private fun DcStateBadge(label: String, state: DataChannelState) {
    val color = when (state) {
        DataChannelState.OPEN -> AppColors.Green
        DataChannelState.CONNECTING -> AppColors.Orange
        DataChannelState.CLOSING -> AppColors.Yellow
        DataChannelState.CLOSED -> AppColors.TextMuted
    }
    Row(
        modifier = Modifier
            .background(color.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(Modifier.size(6.dp).background(color, CircleShape))
        Text(label, color = color, fontSize = 10.sp)
    }
}

// ─────────────────────────────────────────────────────────
@Composable
private fun DcMessageBubble(msg: DcMessage) {
    val bubbleColor = when {
        msg.isOutgoing -> AppColors.Blue.copy(alpha = 0.8f)
        msg.isBinary -> AppColors.Orange.copy(alpha = 0.3f)
        else -> AppColors.CardBorder.copy(alpha = 0.6f)
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (msg.isOutgoing) Arrangement.End else Arrangement.Start
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 280.dp)
                .background(bubbleColor, RoundedCornerShape(8.dp))
                .padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
            Text(
                text = msg.text,
                color = AppColors.TextPrimary,
                fontSize = 12.sp
            )
        }
    }
}
