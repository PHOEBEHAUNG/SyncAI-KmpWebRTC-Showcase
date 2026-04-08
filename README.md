# VLMWebRTC

VLM WebRTC Streaming App - 一個 Kotlin Multiplatform 專案，支援 Android、iOS、Web、Desktop (JVM)。

## 📋 目錄

- [專案架構](#專案架構)
- [功能特性](#功能特性)
- [依賴關係](#依賴關係)
- [建置與執行](#建置與執行)

---

## 專案架構

```
VLMWebRTC/
├── composeApp/                     # KMP 共用程式碼
│   └── src/
│       ├── commonMain/             # 所有平台共用
│       │   └── kotlin/
│       │       └── com/codingdrama/vlmwebrtc/
│       │           ├── App.kt                  # 主應用程式
│       │           ├── audio/                  # 音訊模組
│       │           │   ├── AudioPushPlayer.kt  # expect 音訊推送播放器
│       │           │   └── WhipSignaling.kt    # WHIP 信令協議
│       │           └── ...
│       ├── androidMain/            # Android 實作
│       │   └── kotlin/.../audio/
│       │       └── AudioPushPlayer.android.kt
│       ├── iosMain/                # iOS 實作
│       │   └── kotlin/.../audio/
│       │       └── AudioPushPlayer.ios.kt
│       ├── jvmMain/                # JVM/Desktop 實作
│       │   └── kotlin/.../audio/
│       │       └── AudioPushPlayer.jvm.kt
│       ├── jsMain/                 # JavaScript (瀏覽器) 實作
│       │   └── kotlin/.../audio/
│       │       └── AudioPushPlayer.js.kt
│       └── wasmJsMain/             # WebAssembly 實作
│           └── kotlin/.../audio/
│               └── AudioPushPlayer.wasmJs.kt
├── iosApp/                         # iOS 應用入口 (Xcode 專案)
│   ├── Podfile                     # CocoaPods 依賴
│   └── iosApp/
├── build-ios.sh                    # iOS 建置腳本
└── build.gradle.kts
```

### 模組說明

| 模組 | 說明 |
|------|------|
| `commonMain` | 平台無關的共用程式碼，包含 expect 宣告 |
| `androidMain` | Android 平台 actual 實作 |
| `iosMain` | iOS 平台 actual 實作 (使用 GoogleWebRTC) |
| `jvmMain` | JVM/Desktop 平台 actual 實作 (使用 webrtc-java) |
| `jsMain` | JavaScript 瀏覽器 actual 實作 (使用原生 RTCPeerConnection) |
| `wasmJsMain` | WebAssembly 瀏覽器 actual 實作 |

---

## 功能特性

- 🎥 **視訊接收** - 透過 WHEP 協議接收 WebRTC 視訊串流
- 🎤 **音訊推送** - 透過 WHIP 協議發送麥克風音訊
- 📱 **跨平台支援** - Android、iOS、Web、Desktop
- 🔄 **自動重連** - 可配置的指數退避重試機制

---

## 依賴關係

本專案依賴 `kotlin-webrtc-client` SDK：

```kotlin
// build.gradle.kts
implementation("com.codingstable:kotlin-webrtc-client:1.1.0")
```

### SDK 提供的功能

| 功能 | 狀態 | 說明 |
|------|------|------|
| `WebRTCClient` | ✅ | WebRTC 客戶端核心 API |
| `VideoRenderer` | ✅ | 跨平台視訊渲染 Composable |
| `WhepSignaling` | ✅ | WHEP 信令 (接收串流) |
| `WhipSignaling` | ⚠️ App 內實作 | WHIP 信令 (發送串流) - SDK 尚未提供 |
| `AudioPushPlayer` | ⚠️ App 內實作 | 音訊推送播放器 - SDK 尚未提供 |

> ⚠️ **注意**: `WhipSignaling` 和 `AudioPushPlayer` 目前在 App 內實作，待 SDK 更新後可移除。

---

## 建置與執行

### Android

```bash
# macOS/Linux
./gradlew :composeApp:assembleDebug

# Windows
.\gradlew.bat :composeApp:assembleDebug
```

### iOS

使用建置腳本：

```bash
# 模擬器 (Debug)
./build-ios.sh

# 實機 (Debug)
./build-ios.sh device

# 實機 (Release)
./build-ios.sh device release
```

或在 Xcode 中開啟 `iosApp/iosApp.xcworkspace`。

### Desktop (JVM)

```bash
# macOS/Linux
./gradlew :composeApp:run

# Windows
.\gradlew.bat :composeApp:run
```

### Web (WASM)

```bash
# macOS/Linux
./gradlew :composeApp:wasmJsBrowserDevelopmentRun

# Windows
.\gradlew.bat :composeApp:wasmJsBrowserDevelopmentRun
```

### Web (JavaScript)

```bash
# macOS/Linux
./gradlew :composeApp:jsBrowserDevelopmentRun

# Windows
.\gradlew.bat :composeApp:jsBrowserDevelopmentRun
```

---

## 相關專案

- [kotlin-webrtc-client](../kotlin-webrtc-client) - Kotlin Multiplatform WebRTC SDK
- [mediamtx](../mediamtx) - MediaMTX 串流伺服器設定

---

## License

MIT License
