# 📦 WearAppUpdater (v1.0.0)

[![Wear OS](https://img.shields.io/badge/Wear%20OS-5.0-blue.svg)](https://developer.android.com/wear)
[![Device](https://img.shields.io/badge/Target-Samsung%20Galaxy%20Watch%206-black.svg)](https://www.samsung.com)
[![License](https://img.shields.io/badge/License-MIT-green.svg)](LICENSE)

**WearAppUpdater** is a dedicated Wear OS App Store & App Manager for Samsung Galaxy Watches. It scans your personal GitHub repositories, queries the GitHub Releases API for the latest published releases (`v1.x.x`), compares them against your installed app versions, and enables **one-tap download and installation** directly on your smartwatch!

Developed by **Aju George** ([@ajimsjames](https://github.com/ajimsjames)).

---

## ✨ Features

- 📦 **Monitored Smartwatch Repositories**:
  - 🏥 **WearHealthSuite** (`com.example.wearhealthsuite`)
  - 📡 **WearBLEScanner** (`com.example.wearblescanner`)
  - 🎈 **WearBaroAlt** (`com.example.wearbaroalt`)
  - 🌐 **WearOSBrowser** (`com.example.wearosbrowser`)
  - ⚡ **WearFileServer** (`com.example.wearfileserver`)
  - 📁 **WearFileManager** (`com.example.wearosfilemanager`)
  - 🩺 **WearDiagnostics** (`com.example.weardiagnostics`)
  - 🗺️ **WearMaps** (`com.example.wearmaps`)
  - 🧭 **WearCompass** (`com.example.wearcompass`)
  - 📶 **WearWifiTools** (`com.example.wearwifitools`)
  - 📄 **WearPDFReader** (`com.example.wearpdfreader`)
- 🔍 **Live Version Comparison**:
  - Compares local installed Package Version against the latest GitHub Release tag (`tag_name`).
  - Badges: `⚡ UPDATE AVAILABLE`, `🟢 Up to date`, `📥 NOT INSTALLED`.
- ⚡ **Direct APK Download & Installation**:
  - Downloads release APKs (`app-release.apk`) directly from GitHub Releases asset URLs.
  - Launches Android Package Installer (`FileProvider`) on the watch for instant one-tap updates.
- 📐 **Wear OS Curved Bezel Optimization**: `38.dp` top padding for unobstructed reading on round displays.

---

## 🛠️ Technology Stack

* **Platform**: Android Wear OS (Min SDK 30 / Target SDK 33)
* **Language**: Kotlin 1.9
* **UI**: Jetpack Compose for Wear OS
* **Installer Engine**: Android `FileProvider` + `PackageInstaller` Intent (`application/vnd.android.package-archive`)

---

## 🚀 Installation via Wireless ADB

```bash
adb connect <WATCH_IP>:<PORT>
adb install -r WearAppUpdater-v1.0.0.apk
```

---

## 👨‍💻 Author

Developed by **Aju George**  
GitHub: [@ajimsjames](https://github.com/ajimsjames)
