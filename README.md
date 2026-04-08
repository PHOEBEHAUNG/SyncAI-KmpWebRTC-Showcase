# SyncAI-KmpWebRTC-Showcase

A Kotlin Multiplatform (KMP) showcase app demonstrating the features and API of [SyncAI-Lib-KmpWebRTC](https://github.com/Syncrobotic/SyncAI-Lib-KmpWebRTC) — a cross-platform WebRTC library for Android, iOS, and JVM Desktop.

## Table of Contents

- [About This App](#about-this-app)
- [Project Structure](#project-structure)
- [Platform Support](#platform-support)
- [Showcase Screens](#showcase-screens)
- [Library Components Demonstrated](#library-components-demonstrated)
- [Adding the Library Dependency](#adding-the-library-dependency)
- [Build & Run](#build--run)
- [Related Links](#related-links)

---

## About This App

This app is the companion showcase for `SyncAI-Lib-KmpWebRTC`. It demonstrates how to integrate and use the library's core APIs in a real Compose Multiplatform app, covering:

1. **All `MediaConfig` modes** — receive video (WHEP), send audio/video (WHIP), bidirectional audio, and video call modes, each shown with minimal working code.
2. **DataChannel messaging** — sending and receiving text and binary messages over a WebRTC data channel.
3. **Multi-session / Multi-view** — running multiple independent `WebRTCSession` instances simultaneously and rendering several streams in a grid layout.
4. **Real-time stats** — displaying live connection metrics (`WebRTCStats`) including bitrate, RTT, and packet loss.

---

## Project Structure

```
SyncAI-KmpWebRTC-Showcase/
├── composeApp/
│   └── src/
│       ├── commonMain/kotlin/com/codingdrama/vlmwebrtc/
│       │   ├── App.kt                # App entry point & tab navigation
│       │   ├── DataChannelScreen.kt  # DataChannel messaging demo
│       │   ├── DualSessionScreen.kt  # Two independent sessions simultaneously
│       │   ├── MultiViewScreen.kt    # Multi-stream grid view (1×2 / 2×2)
│       │   ├── Greeting.kt           # Platform greeting helper
│       │   ├── Platform.kt           # expect platform abstraction
│       │   └── permission/
│       │       └── Permission.kt     # Cross-platform permission abstraction
│       ├── androidMain/              # Android platform implementations
│       ├── iosMain/                  # iOS platform implementations
│       └── jvmMain/                  # JVM/Desktop platform implementations
├── iosApp/                           # iOS Xcode project
│   ├── Podfile
│   └── iosApp.xcworkspace
└── build.gradle.kts
```

---

## Platform Support

| Platform | Status | Notes |
|----------|--------|-------|
| **Android** (physical device) | ✅ | Primary showcase target |
| **iOS arm64** (physical device) | ✅ | Primary showcase target |
| **JVM Desktop** (macOS / Linux) | ✅ | Useful as an additional viewer |
| iOS Simulator | ❌ | Not supported by the library |
| JavaScript / WasmJS | ❌ | Not yet supported by the library |

---

## Showcase Screens

The app is organized into four tabs, each showcasing a different aspect of the library:

### 📡 Single — Core Session API

The primary screen for exploring the library's session management.

- Enter any WHEP / WHIP endpoint URL.
- Select a `MediaConfig` mode from the chip strip:
  | Mode | `MediaConfig` | Description |
  |------|--------------|-------------|
  | Receive Video | `RECEIVE_VIDEO` | Pull a video stream via WHEP |
  | Send Video | `SEND_VIDEO` | Push camera video via WHIP with `CameraPreview` |
  | Send Audio | `SEND_AUDIO` | Push microphone audio via WHIP |
  | Intercom | `BIDIRECTIONAL_AUDIO` | Full-duplex audio session |
  | Video Call | `VIDEO_CALL` | Remote video + local camera PiP overlay |
- Live `SessionState` indicator (Idle / Connecting / Connected / Reconnecting / Error / Closed).
- `WebRTCStats` panel showing bitrate, RTT, and packet loss when connected.
- Mute, video toggle, and camera-switch controls for supported modes.

### 💬 DataChannel — Messaging over WebRTC

Demonstrates the `DataChannel` API for sending and receiving messages.

- Connect via WHIP or WHEP with a configurable endpoint URL.
- Create up to two data channels with configurable names and reliability.
- Send text messages and view incoming text / binary messages in a scrollable log.
- Displays per-channel `DataChannelState` (connecting / open / closing / closed).

### 📺 Multi-View — Multiple Simultaneous Streams

Showcases running multiple `WebRTCSession` instances at the same time with a grid layout.

- Toggle between **1×2** (two streams) and **2×2** (four streams) grid layouts.
- Each cell has its own URL input and independent connect / disconnect lifecycle.
- Supports `RECEIVE_VIDEO` and `VIDEO_CALL` modes per cell.
- Per-session `SessionState` dot indicator in each cell overlay.

### 🔀 Dual — Two Independent Sessions

Shows how to run two fully independent sessions side by side.

- **Session A** — optimized for receiving video (`RECEIVE_VIDEO`) or a video call (`VIDEO_CALL`).
- **Session B** — optimized for sending audio (`SEND_AUDIO`), intercom (`BIDIRECTIONAL_AUDIO`), or sending video (`SEND_VIDEO`).
- Each panel has its own URL input, mode selector, and media controls.

---

## Library Components Demonstrated

| Component | Description |
|-----------|-------------|
| `WebRTCSession` | Unified session manager for WHEP, WHIP, and bidirectional streams |
| `HttpSignalingAdapter` | HTTP-based WHEP/WHIP signaling adapter |
| `MediaConfig` | Declares the media direction and type of a session |
| `SessionState` | Reactive state flow: Idle → Connecting → Connected → Reconnecting / Error / Closed |
| `VideoRenderer` | Cross-platform Composable for rendering a remote video stream |
| `CameraPreview` | Cross-platform Composable for local camera preview |
| `AudioPushPlayer` | Composable that manages microphone capture and audio push |
| `WebRTCStats` | Real-time connection metrics (bitrate, RTT, packet loss) |
| `DataChannel` | Send and receive text/binary messages over a WebRTC data channel |
| `DataChannelConfig` | Configuration for reliable or unreliable data channels |
| `DataChannelListenerAdapter` | Callback adapter for data channel events and incoming messages |

---

## Adding the Library Dependency

```kotlin
// settings.gradle.kts
dependencyResolutionManagement {
    repositories {
        mavenLocal()        // for local development builds
        // mavenCentral()   // for published releases
    }
}

// composeApp/build.gradle.kts
commonMain.dependencies {
    implementation("com.syncrobotic:syncai-lib-kmpwebrtc:1.1.0")
}
```

---

## Build & Run

### Prerequisites

- **Android:** Android Studio (or `./gradlew`) with Android SDK installed
- **iOS:** Xcode 15+, CocoaPods (`pod install`)
- **JVM:** JDK 11+

### Android

```bash
./gradlew :composeApp:assembleDebug
# or run directly from Android Studio
```

### iOS (physical device only)

```bash
# Install Pods (first time)
cd iosApp && pod install && cd ..

# Open in Xcode
open iosApp/iosApp.xcworkspace
```

> ⚠️ **iOS physical device (arm64) only.** The library does not support the iOS Simulator.

### JVM Desktop

```bash
./gradlew :composeApp:run
```

### Publishing the Library to Local Maven (for local development)

```bash
# Run from the SyncAI-Lib-KmpWebRTC project directory
./gradlew publishToMavenLocal
```

---

## Related Links

- [SyncAI-Lib-KmpWebRTC](https://github.com/Syncrobotic/SyncAI-Lib-KmpWebRTC) — The library this app showcases

---

## License

MIT License
