# Smart5G: Indoor Cellular Network Analyzer & Coverage Optimizer

[![Android](https://img.shields.io/badge/Platform-Android-3DDC84.svg?style=flat&logo=android)](https://developer.android.com)
[![Kotlin](https://img.shields.io/badge/Language-Kotlin%202.0-7F52FF.svg?style=flat&logo=kotlin)](https://kotlinlang.org)
[![Compose](https://img.shields.io/badge/UI-Jetpack%20Compose%20%2F%20Material%203-4285F4.svg?style=flat&logo=jetpackcompose)](https://developer.android.com/jetpack/compose)
[![MinSdk](https://img.shields.io/badge/Min%20SDK-26%20(Android%208.0)-brightgreen.svg)](https://developer.android.com)
[![TargetSdk](https://img.shields.io/badge/Target%20SDK-34%20(Android%2014)-blue.svg)](https://developer.android.com)
[![Latest Release](https://img.shields.io/github/v/release/ares-thanatos/smart5g?color=blue&label=Latest%20Release)](https://github.com/ares-thanatos/smart5g/releases)
[![Download APK](https://img.shields.io/badge/Download-Smart5G.apk-success.svg?logo=android)](https://github.com/ares-thanatos/smart5g/releases/latest)

**Smart5G** is a modern Android application designed for real-time cellular telemetry diagnostics, multi-stream speed benchmarking, and room-by-room indoor signal mapping. It helps users discover cellular dead zones, evaluate real-world 5G/4G connectivity, and identify optimal placement for 5G Home Broadband (Fixed Wireless Access / CPE) routers and remote work desks.


---

> ### ⚠️ Important Disclaimer
> **Smart5G is a network diagnostic analyzer and indoor connectivity optimization system.**  
> It is **NOT** a physical cellular signal amplifier or booster.  
> 
> Smart5G **does not**:
> - Physically boost or amplify radio wave reception.
> - Guarantee maximum 5G speeds or override carrier bandwidth limits.
> - Bypass carrier throttling, network management policies, or SIM provisioning.
>
> Cellular performance is fundamentally determined by carrier infrastructure, distance and line-of-sight to the nearest cell tower, radio frequency band propagation, building materials, and modem hardware capabilities. Smart5G empowers users with transparent, empirical measurement to find the best physical locations within their environment.

---

## 📋 Table of Contents
- [Project Objective](#-project-objective)
- [Main Features](#-main-features)
- [Cellular Measurement & Diagnostic Metrics](#-cellular-measurement--diagnostic-metrics)
- [Smart5G Quality Score Algorithm](#-smart5g-quality-score-algorithm)
- [Technology Stack & Architecture](#-technology-stack--architecture)
- [Device Compatibility & Permissions](#-device-compatibility--permissions)
- [Known Limitations & Hardware Variance](#-known-limitations--hardware-variance)
- [Installation Instructions](#-installation-instructions)
- [Building & Development](#-building--development)
- [Development Workflow](#-development-workflow)
- [Future Roadmap](#-future-roadmap)

---

## 🎯 Project Objective

Higher-frequency 5G signals (such as mid-band C-Band and mmWave) offer high bandwidth and low latency, but suffer from rapid signal attenuation when passing through exterior walls, Low-E glass windows, reinforced concrete, and interior barriers. Consequently, indoor cellular performance can fluctuate drastically by 10× to 50× between different rooms or even across opposite corners of the same room.

**Smart5G solves this problem by:**
1. Providing accurate, low-level cellular telemetry without simulated or fake indicators.
2. Running repeatable, multi-stream speed tests against global edge servers.
3. Enabling systematic room-by-room signal surveys with statistical variance tracking.
4. Translating complex RF metrics into actionable use-case readiness ratings (Gaming, 4K Streaming, Video Calls, and 5G CPE Router placement).

---

## 🚀 Main Features

### 1. Live 4G/5G Telemetry & Hardware Diagnostics
- **5G Network Type Differentiation**:
  - **5G SA (Standalone)**: Pure 5G NR core network (`NETWORK_TYPE_NR`).
  - **5G NSA (Non-Standalone)**: Dual connectivity with an LTE anchor carrier and secondary NR data carriers (`TelephonyDisplayInfo` override detection).
  - **4G LTE** and legacy fallback (3G UMTS, 2G GSM).
- **Core Signal Indicators**: RSRP (dBm) with an animated color-coded progress bar, RSRQ (dB), and SINR (dB).
- **Baseband Diagnostic Identifiers**: Physical Cell ID (`PCI`), Cell Identity (`CI`), Tracking Area Code (`TAC`), and frequency channel numbers (`NR-ARFCN` / `EARFCN`).
- **Dual SIM & Multi-Carrier Support**: Automatically identifies active subscription slots and carrier names (e.g. `SIM 1: Jio 5G • SIM 2: Airtel 5G`).
- **Active Wi-Fi Detection**: Warns the user when Wi-Fi is active so speed tests measure real cellular data instead of home broadband.

### 2. Live Radial Speedometer & Multi-Stream Benchmark
- **Speedometer Dial**: Custom 240-degree radial gauge with animated sweep arc, glowing needle pointer, and large digital Mbps readout.
- **TLS-Compensated Latency & Jitter**: Isolates pure packet round-trip time from initial TLS handshake overhead.
- **Parallel Multi-Stream Throughput**: Multi-threaded parallel streams against Cloudflare edge network with 5-second socket safety timeouts.
- **Real-Time Telemetry Feed**: Reports instantaneous Mbps updates and stage progression (Ping -> Download -> Upload).

### 3. Indoor Room Survey & Coverage Heatmapping
- **Quick Room Presets**: One-tap selection chips (`Living Room`, `Home Office`, `Master Bedroom`, `Kitchen`, `Balcony`, `Basement`) or custom labels.
- **Multi-Sample Averaging**: Takes 4 consecutive RSRP measurements over several seconds to calculate median signal strength and RF variance spread (detecting multipath reflection).
- **Persistent Offline Storage**: Automatically stores survey history locally using JSON-backed storage, persisting across app restarts.
- **⭐ Best Room Highlighting**: Automatically identifies and ranks the best room in your building for 5G router placement.
- **One-Tap Report Sharing**: Generates a formatted survey report shareable via the native Android Share Sheet (SMS, WhatsApp, Email, Notes).

### 4. Live Signal Stability Timeline Graph
- A rolling Canvas sparkline graph plotting the last 30 RSRP samples in real time.
- Area gradient fill and Min/Max dBm tracking to visualize signal fading as you walk through your home or office.

### 5. Adaptive Theming
- Deep AMOLED Dark Theme (`#0F141C`) optimized for OLED power efficiency during testing.
- Clean Light Theme (`#F6F8FA`).
- Manual quick-toggle icon in the top app bar or automatic synchronization with system theme.

---

## 📡 Cellular Measurement & Diagnostic Metrics

Smart5G directly reads raw hardware values from Android telephony baseband APIs:

| Metric | Name | Standard Unit | Ideal Range (3GPP) | What It Measures |
| :--- | :--- | :--- | :--- | :--- |
| **RSRP** | Reference Signal Received Power | dBm | ≥ -85 dBm (Excellent)<br>-86 to -98 dBm (Good)<br>-99 to -110 dBm (Fair)<br>< -110 dBm (Poor) | Overall cellular signal strength received from the base station. |
| **RSRQ** | Reference Signal Received Quality | dB | -3 dB to -10 dB (Good)<br>< -15 dB (Congested / Poor) | Signal quality and interference levels across all resource blocks. |
| **SINR** | Signal to Interference-plus-Noise | dB | ≥ 20 dB (Excellent)<br>10 to 20 dB (Good)<br>< 0 dB (Heavy noise) | Clarity of the cellular signal relative to background noise and co-channel interference. |
| **PCI** | Physical Cell ID | Integer | 0 to 1007 (NR) | Identifies the physical sector of the serving cellular tower. |
| **ARFCN** | Absolute RF Channel Number | Integer | Band-specific | Identifies the exact carrier frequency channel used by the modem. |

---

## 📊 Smart5G Quality Score Algorithm

The **Smart5G Quality Score** is an empirical 0–100 rating that balances real-world throughput, latency, jitter, and radio signal strength:

$$\text{Quality Score} = \frac{0.30 \cdot S_{\text{Down}} + 0.15 \cdot S_{\text{Up}} + 0.20 \cdot S_{\text{Lat}} + 0.10 \cdot S_{\text{Jit}} + 0.25 \cdot S_{\text{RSRP}}}{\sum \text{Weights of Available Metrics}}$$

### Metric Normalization Curves
- **Download ($S_{\text{Down}}$)**: Evaluated up to 100 Mbps (100 Mbps = 100).
- **Upload ($S_{\text{Up}}$)**: Evaluated up to 50 Mbps (50 Mbps = 100).
- **Latency ($S_{\text{Lat}}$)**: $100 - (\text{latency\_ms} / 1.5)$ (≤ 20 ms = 100; 150 ms = 0).
- **Jitter ($S_{\text{Jit}}$)**: $100 - (\text{jitter\_ms} \times 4)$ (≤ 3 ms = 100; 25 ms = 0).
- **RSRP Signal ($S_{\text{RSRP}}$)**: $(\text{RSRP} + 120) \times 2.0$ (≥ -70 dBm = 100; ≤ -120 dBm = 0).

### Use-Case Readiness Thresholds
- 🎮 **Online Gaming**: Requires Latency ≤ 45 ms and Jitter ≤ 10 ms.
- 🎬 **4K / UHD Streaming**: Requires Download ≥ 25 Mbps and Latency ≤ 120 ms.
- 📹 **Work & Video Calls**: Requires Download ≥ 8 Mbps, Upload ≥ 3 Mbps, Latency ≤ 80 ms, Jitter ≤ 20 ms.
- 📡 **5G Router Placement**: Overall Score ≥ 72 and RSRP ≥ -96 dBm.

---

## 🛠 Technology Stack & Architecture

- **Language**: Kotlin 2.0.20
- **UI Toolkit**: Jetpack Compose BOM 2024.09.00 with Material Design 3
- **Architecture**: MVVM (Model-View-ViewModel) with Kotlin Coroutines and StateFlow
- **Platform APIs**:
  - `android.telephony.TelephonyManager`
  - `android.telephony.TelephonyCallback` (Android 12+ / API 31+)
  - `android.telephony.PhoneStateListener` (Backwards-compatible fallback for API 26–30)
  - `android.telephony.SubscriptionManager` (Multi-SIM handling)
  - `android.net.ConnectivityManager` & `NetworkCapabilities` (Wi-Fi detection)
- **JSON Serialization**: Google Gson 2.11.0
- **HTTP Engine**: `java.net.HttpURLConnection` with multi-stream coroutine dispatchers
- **Build System**: Gradle 8.7 with Android Gradle Plugin 8.5.2

### Architecture Overview

```text
Android Device
      │
      ▼
Android Telephony & Network APIs
      │
      ▼
Cellular Measurement Layer
      │
      ├── Signal Strength (RSRP, RSRQ, SINR)
      ├── Tower Identifiers (PCI, CI, TAC, ARFCN)
      └── Network Technology (5G SA, 5G NSA, 4G LTE)
      │
      ▼
Network Performance Benchmark Engine
      │
      ├── Latency & Jitter
      ├── Multi-Threaded Download
      └── Parallel Upload
      │
      ▼
Smart5G Quality & Recommendation Engine
      │
      ▼
Presentation Layer (Jetpack Compose M3)
      │
      ├── Live Dashboard & Radial Speedometer
      ├── Room Coverage Survey & Heatmapping
      └── Diagnostic Tower Telemetry Table
```

---


## 📱 Device Compatibility & Permissions

### System Requirements
- **Operating System**: Android 8.0 Oreo (API level 26) or higher.
- **Target SDK**: Android 14 (API level 34).
- **Hardware Requirement**: **Physical Android device with an active cellular SIM card**.  
  *(Android Studio emulators simulate a virtual network without a physical cellular modem and cannot report real RSRP, RSRQ, or 5G NR parameters).*

### Android Permissions
| Permission | Reason Required |
| :--- | :--- |
| `android.permission.READ_PHONE_STATE` | Required to query carrier name, network type, and active SIM subscriptions. |
| `android.permission.ACCESS_FINE_LOCATION` | Required by the Android OS security model to query cell tower signal strength and cell IDs (prevents unauthorized cell-tower triangulation). |
| `android.permission.ACCESS_COARSE_LOCATION` | Required alongside fine location starting with Android 12 (API 31). |
| `android.permission.ACCESS_NETWORK_STATE` | Detects network availability and active transport type. |
| `android.permission.ACCESS_WIFI_STATE` | Detects when Wi-Fi is actively routing data. |
| `android.permission.INTERNET` | Executes speed tests against public edge endpoints. |

---

## ⚠️ Known Limitations & Hardware Variance

1. **Modem Driver Reporting Differences**:
   - Different Android chipset vendors (Qualcomm Snapdragon, MediaTek Dimensity, Samsung Exynos, Google Tensor) implement cellular HAL (Hardware Abstraction Layer) callbacks differently.
   - Some vendor drivers omit `SINR` or secondary component carrier info in 5G NSA mode. Any unavailable metric is shown as `"Unavailable"` rather than displaying fabricated numbers.
2. **5G NSA Display Timing**:
   - In 5G NSA (Non-Standalone), the device connects to an LTE anchor cell and establishes 5G NR radio links dynamically when heavy data transfer begins. Idle devices may momentarily display LTE until traffic commences.
3. **Physical Obstacles**:
   - Low-E window coatings, metallized window films, and thick concrete walls can attenuate 5G mid-band signals by 15–30 dB.

---

## 📦 Installation Instructions

### Method 1: Installing the Pre-Built APK via ADB (Recommended)
If you have your phone connected to your computer via USB with USB Debugging enabled:

```powershell
# Check connected device
adb devices

# Install APK directly
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### Method 2: Manual Device Transfer
1. Transfer `Smart5G.apk` to your phone via USB file transfer, Google Drive, or messaging apps.
2. Open the file on your device and tap **Install** (enable *"Install from unknown sources"* if prompted).
3. Open **Smart5G**, grant phone and location permissions, and disable Wi-Fi for testing.

---

## 💻 Building & Development

### Prerequisites
- Android Studio Koala (2024.1.1) or newer
- JDK 17 or JDK 21 (configured in `JAVA_HOME`)
- Android SDK Platform 34 and Build-Tools 34.0.0

### Build from Command Line
```powershell
# Clone the repository
git clone https://github.com/sathya007/smart_5g.git
cd smart_5g

# Build Debug APK
.\gradlew assembleDebug

# Output APK path:
# app/build/outputs/apk/debug/app-debug.apk
```

---

## 🔄 Development Workflow

When contributing or updating the project:

```powershell
# 1. Check changed files
git status

# 2. Stage changes
git add .

# 3. Commit with a descriptive message
git commit -m "Describe your changes"

# 4. Push to GitHub
git push
```

---

## 🗺️ Future Roadmap

- [ ] **Floorplan Blueprint Canvas**: Allow importing a 2D floorplan image and tapping rooms to generate an interpolated signal heatmap overlay.
- [ ] **Custom Benchmark Server Selection**: Add server endpoints in Europe, North America, and Asia with custom payload sizing.
- [ ] **PDF Survey Export**: Export full surveys with visual charts as a branded PDF document.
- [ ] **24-Hour Signal Logger**: Background foreground service to measure carrier deprioritization and tower congestion throughout the day.
- [ ] **Cell Tower Compass**: Estimate direction to the connected tower using crowdsourced cell tower databases (OpenCelliD).

---

## 📄 License
This project is open-source under the [Apache License 2.0](LICENSE).
