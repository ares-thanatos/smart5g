# Smart5G 📡

**Universal Android 4G/5G Network Analyzer & Indoor Connectivity Optimization Prototype**

Smart5G is an Android application designed to measure real mobile-network conditions and analyze cellular connectivity across different Android devices.

## 🎯 Objective

Smart5G aims to answer:

> **Where and under what conditions does my phone get the best available mobile-network performance?**

The application collects available cellular measurements and combines them with real Internet-performance measurements.

## Features

* 📡 4G/5G network detection
* 📊 RSRP / RSRQ / SINR where supported
* 📶 Cellular information
* 🚀 Download speed measurement
* ⬆️ Upload speed measurement
* ⚡ Latency measurement
* 📈 Live network monitoring
* 🏠 Indoor room testing
* 📍 Best measured location detection
* 📱 Multi-device compatibility detection
* 📊 Smart5G Quality Score
* 🔐 Permission-aware design

## Architecture

```text
Android Device
      │
      ▼
Android Telephony APIs
      │
      ▼
Cellular Measurement Layer
      │
      ├── Signal Data
      ├── Cell Information
      └── Network Type
      │
      ▼
Network Performance Engine
      │
      ├── Download
      ├── Upload
      └── Latency
      │
      ▼
Smart5G Quality Engine
      │
      ▼
Dashboard / Room Test
```

## Technology

* Kotlin
* Android
* Jetpack Compose
* Android Telephony APIs
* Kotlin Coroutines / Flow
* Android Network APIs

## Important Limitation

Smart5G does **not** physically amplify cellular radio signals.

An Android application cannot simply increase the RF power of a phone's cellular modem.

Instead, Smart5G measures available network conditions and identifies locations and conditions where the device achieves better real-world performance.

## Device Compatibility

Cellular APIs differ between:

* Android versions
* manufacturers
* modem chipsets
* carriers
* device models

Therefore, Smart5G uses capability detection and displays `Unavailable` when a particular measurement cannot be accessed.

The application does not generate fake network measurements.

## Current Status

🚧 **Version 1 — Prototype**

Current development focus:

* Real cellular measurement
* 4G/5G detection
* Network performance testing
* Indoor testing
* Cross-device compatibility

## Future Development

* Network-quality history
* Indoor heatmaps
* Advanced optimization recommendations
* Network-performance prediction
* Optional cloud analytics
* External 5G CPE/antenna integration

## Disclaimer

Smart5G is a network measurement and analysis project.

It does not bypass carrier restrictions, modify modem firmware, increase cellular transmission power, or interfere with cellular networks.
