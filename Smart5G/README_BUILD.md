# Smart5G - Intelligent 5G Coverage & Room Survey Optimizer

Smart5G is an Android application built with modern Jetpack Compose and Material 3 for real-time cellular telemetry, precision network speed benchmarking, and room-by-room signal optimization for home and office environments.

## Features

- **Live 5G/LTE Telemetry**:
  - Distinguishes between **5G Standalone (SA)**, **5G Non-Standalone (NSA)**, **4G LTE**, and legacy cellular networks.
  - Live cellular diagnostic metrics: **RSRP** (signal strength with visual color-coded progress meter), **RSRQ** (signal quality), **SINR** (signal-to-noise ratio).
  - Low-level baseband values: **PCI** (Physical Cell ID), **CI** (Cell Identity), **TAC** (Tracking Area Code), and **ARFCN / EARFCN** frequency channels.
  - Automatic Wi-Fi active detection and warning banner so tests measure real cellular data instead of home broadband.

- **Multi-Stream Speed Test Benchmark**:
  - Live progress updates with animated speedometer/gauge (real-time Mbps) and step indicators:
    1. **Ping & Jitter**: Multi-sample TLS-compensated latency test against Cloudflare edge network.
    2. **Multi-Threaded Download**: Parallel streams with 5-second socket-safe timeout.
    3. **Streaming Upload**: Multi-threaded POST payload benchmarking.

- **Smart5G Quality Score & Actionable Advice**:
  - 0–100 weighted score balancing throughput, latency, jitter, and RSRP signal.
  - **Use-Case Readiness Badges**:
    - 🎮 **Online Gaming**: Evaluates latency (< 45 ms) and packet jitter (< 10 ms).
    - 🎬 **4K / UHD Streaming**: Evaluates sustained download bitrate (> 25 Mbps).
    - 📹 **Work & Video Calls**: Evaluates upload speed (> 3 Mbps) and jitter.
    - 📡 **5G Router Placement**: Highlights whether location is optimal for fixed wireless 5G CPE gateways.
  - Contextual diagnostic advice based on signal variance and multipath wall reflection.

- **Room Survey & Coverage Mapping**:
  - Quick room presets (Living Room, Home Office, Master Bedroom, Kitchen, Balcony, Basement).
  - 4x sample median RSRP and signal variance spread calculation.
  - Persistent offline storage (saved across app restarts).
  - Automatic **⭐ Best Room for 5G Router / Work** highlight.
  - One-tap survey report sharing via Android Share Sheet (SMS, WhatsApp, Email, Notes).

## Building & Running

### Requirements
- **Android Studio Koala or newer** (or command-line Gradle 8.7+).
- **JDK 17 or 21**.
- **Android SDK API 34** (minSdk 26).
- **Physical 5G/4G Android device with an active SIM card** (Android emulators do not possess real cellular modems).

### Command Line Build
```powershell
# Build Debug APK
.\gradlew assembleDebug

# Output APK location:
# app/build/outputs/apk/debug/app-debug.apk
```

### Permissions
The app requests:
- `READ_PHONE_STATE`: Telephony baseband and carrier identification.
- `ACCESS_FINE_LOCATION` & `ACCESS_COARSE_LOCATION`: Required by Android OS to query cell tower signal strength (RSRP/RSRQ/SINR).
- `ACCESS_NETWORK_STATE` & `ACCESS_WIFI_STATE`: Network interface monitoring.
- `INTERNET`: Speed test benchmarking.
