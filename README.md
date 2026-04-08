# SyncAI-App-KmpWebRTC

SyncAI WebRTC KMP 的範例與整合測試 App，展示並驗證 [SyncAI-Lib-KmpWebRTC](https://github.com/Syncrobotic/SyncAI-Lib-KmpWebRTC) 在真實裝置上的完整功能。

## 目錄

- [關於此 App](#關於此-app)
- [專案架構](#專案架構)
- [平台支援](#平台支援)
- [目前功能](#目前功能)
- [Level 3 測試計畫](#level-3-測試計畫)
- [Library 依賴](#library-依賴)
- [測試基礎建設](#測試基礎建設)
- [建置與執行](#建置與執行)

---

## 關於此 App

此 App 是 `SyncAI-Lib-KmpWebRTC` 的配套範例 App，用於：

1. **展示 Library API 用法** — 以最小可運行的程式碼示範各種 `MediaConfig` 模式
2. **Level 3 手動整合測試** — 在真實 Android/iOS/JVM 裝置上執行端對端驗證
3. **跨平台回歸驗證** — 確保 Library 在 Android、iOS、JVM 三個平台行為一致

---

## 專案架構

```
SyncAI-App-KmpWebRTC/
├── composeApp/
│   └── src/
│       ├── commonMain/kotlin/com/codingdrama/vlmwebrtc/
│       │   ├── App.kt              # 主畫面（收流 + 推流）
│       │   ├── Platform.kt         # expect 平台抽象
│       │   └── permission/
│       │       └── Permission.kt   # 跨平台權限抽象
│       ├── androidMain/            # Android 平台實作
│       ├── iosMain/                # iOS 平台實作
│       └── jvmMain/                # JVM/Desktop 平台實作
├── iosApp/                         # iOS Xcode 專案
│   ├── Podfile
│   └── iosApp.xcworkspace
└── build.gradle.kts
```

---

## 平台支援

| 平台 | 狀態 | 用途 |
|------|------|------|
| **Android** (實機) | ✅ | 主要測試裝置 |
| **iOS arm64** (實機) | ✅ | 主要測試裝置 |
| **JVM Desktop** (macOS/Linux) | ✅ | 多觀眾測試的第 3+ viewer |
| iOS Simulator | ❌ | Library 不支援 |
| JavaScript / WasmJS | ❌ | Library 尚未支援 |

---

## 目前功能

| 功能 | MediaConfig | 狀態 |
|------|-------------|------|
| 接收視訊串流 (WHEP) | `RECEIVE_VIDEO` | ✅ |
| 推送麥克風音訊 (WHIP) | `SEND_AUDIO` | ✅ |
| 推送鏡頭視訊 (WHIP) | `SEND_VIDEO` | 🚧 規劃中 |
| 雙向音訊通話 | `BIDIRECTIONAL_AUDIO` | 🚧 規劃中 |
| 雙向視訊通話 | `VIDEO_CALL` | 🚧 規劃中 |
| DataChannel 訊息 | 自訂 MediaConfig | 🚧 規劃中 |
| 多個 VideoRenderer | 多 session | 🚧 規劃中 |

---

## Level 3 測試計畫

此 App 需要支援 [SyncAI-Lib-KmpWebRTC Level 3 測試規格](https://github.com/Syncrobotic/SyncAI-Lib-KmpWebRTC/blob/main/docs/LEVEL3_MANUAL_TEST_GUIDE.md) 中的 Client 端測試（C-1 ~ C-5，共 23 項）。

### C-1: Bidirectional Call (5 項)
- `VIDEO_CALL` / `BIDIRECTIONAL_AUDIO` 雙向 session
- `CameraPreview` 本地鏡頭預覽
- 鏡頭切換（前/後鏡頭）
- Mute / 關閉視訊 controls
- DataChannel + 視訊同時運作

### C-2: External IoT (5 項)
- URL 輸入欄位（可指定任意 endpoint）
- `WebRTCStats` 即時顯示（bitrate / latency / packet loss）
- 雙 session 同時（`RECEIVE_VIDEO` + `SEND_AUDIO`）
- DataChannel JSON 指令 UI

### C-3: Multiple VideoRenderer (4 項)
- 2~4 個 `VideoRenderer` 並排 / Grid 排版
- 每個 session 獨立 connect / close
- per-session `SessionState` 顯示

### C-4: 1-to-N (4 項)
- `SEND_VIDEO` 推鏡頭影像（含 JVM Desktop）
- `CameraPreview` 本地預覽
- Reconnect 狀態顯示

### C-5: DataChannel (5 項)
- 文字 / Binary 訊息發送與接收 UI
- 多 DataChannel 管理
- 高頻訊息吞吐量顯示

---

## Library 依賴

```kotlin
// settings.gradle.kts
dependencyResolutionManagement {
    repositories {
        mavenLocal()   // 本地開發使用
        // 或 GitHub Packages（發布版本）
    }
}

// composeApp/build.gradle.kts
implementation("com.syncrobotic:syncai-lib-kmpwebrtc:1.1.0")
```

### Library 提供的核心元件

| 元件 | 說明 |
|------|------|
| `WebRTCSession` | 統一的 session 管理（WHEP/WHIP/雙向） |
| `HttpSignalingAdapter` | HTTP WHEP/WHIP 信令適配器 |
| `MediaConfig` | 媒體方向設定（RECEIVE_VIDEO / SEND_VIDEO / SEND_AUDIO / VIDEO_CALL ...） |
| `VideoRenderer` | 跨平台視訊渲染 Composable |
| `CameraPreview` | 本地鏡頭預覽 Composable |
| `AudioPushPlayer` | 麥克風推流 Composable |
| `WebRTCStats` | 即時連線統計（bitrate / RTT / packet loss） |

---

## 測試基礎建設

Level 3 測試需搭配 [SyncAI-Lib-KmpWebRTC](https://github.com/Syncrobotic/SyncAI-Lib-KmpWebRTC) 的測試 infra 一起使用。

### 啟動測試 Server

```bash
# 在 SyncAI-Lib-KmpWebRTC 專案下
cd test-infra/

# 啟動 MediaMTX + FFmpeg + Pion IoT
docker compose up -d

# 啟動 SignalingProxy (JVM in-process)
cd ..
./gradlew jvmTest --tests "com.syncrobotic.webrtc.level3.server.SignalingProxyServerTest"
```

### 服務一覽

| 服務 | 位址 | 用途 |
|------|------|------|
| MediaMTX | `<HOST>:8889` | WHEP/WHIP media server |
| SignalingProxy | `<HOST>:8090` | BE 信令代理（S-2, S-5 測試用） |
| Pion IoT | `<HOST>:8080` | IoT 裝置模擬（S-3, C-5 測試用） |
| FFmpeg | — | 模擬 IoT 攝影機推 RTSP 串流 |

### 取得本機 LAN IP（給行動裝置連線）

```bash
# macOS
ipconfig getifaddr en0
```

---

## 建置與執行

### 前置需求

- **Android:** Android Studio / `./gradlew` + Android SDK
- **iOS:** Xcode 15+、CocoaPods (`pod install`)
- **JVM:** JDK 11+

### Android

```bash
./gradlew :composeApp:assembleDebug
# 或直接在 Android Studio 執行
```

### iOS（實機）

```bash
# 安裝 Pods（首次）
cd iosApp && pod install && cd ..

# 在 Xcode 開啟
open iosApp/iosApp.xcworkspace
```

> ⚠️ **只支援 iOS 實機（arm64）**，模擬器目前不支援。

### JVM Desktop

```bash
./gradlew :composeApp:run
```

### 發布 Library 到 Local Maven（本地開發）

```bash
# 在 SyncAI-Lib-KmpWebRTC 專案下
./gradlew publishToMavenLocal
```

---

## 相關連結

- [SyncAI-Lib-KmpWebRTC](https://github.com/Syncrobotic/SyncAI-Lib-KmpWebRTC) — Library 主專案
- [Level 3 測試指南](https://github.com/Syncrobotic/SyncAI-Lib-KmpWebRTC/blob/main/docs/LEVEL3_MANUAL_TEST_GUIDE.md)
- [Level 3 Infra 規劃](https://github.com/Syncrobotic/SyncAI-Lib-KmpWebRTC/blob/main/docs/LEVEL3_INFRA_PLAN.md)

---

## License

MIT License
