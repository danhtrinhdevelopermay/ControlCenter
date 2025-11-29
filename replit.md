# Control Center - iOS Style Android App

## Overview
This is an Android Kotlin application that replicates the iOS Control Center with:
- Blur effect for background content using RenderEffect API (Android 12+)
- Swipe down gesture from top-right corner to open
- GitHub Actions workflow for building APK

## Project Type
**Android Native Application** - This project cannot run directly on Replit as it requires Android SDK and emulator. It is designed to be built using GitHub Actions.

## Project Architecture

### Structure
```
ControlCenter/
├── app/src/main/
│   ├── java/com/example/controlcenter/
│   │   └── MainActivity.kt        # Main logic with blur & gestures
│   ├── res/
│   │   ├── layout/               # XML layouts
│   │   ├── drawable/             # Icons and backgrounds
│   │   └── values/               # Colors, strings, themes
│   └── AndroidManifest.xml
├── .github/workflows/
│   └── build-apk.yml             # GitHub Actions CI/CD
└── gradle files
```

### Key Components
1. **MainActivity.kt** - Handles gesture detection, blur effects, and control toggling
2. **RenderEffect API** - Android 12+ blur implementation
3. **SpringAnimation** - Natural bouncy animations
4. **GitHub Actions** - Automated APK building

## How to Build
1. Push to GitHub repository
2. Go to Actions tab
3. Download APK from Artifacts

## Recent Changes
- Nov 29, 2025: Initial project creation with full Control Center UI

## Technical Notes
- Minimum SDK: 31 (Android 12)
- Target SDK: 34
- Kotlin version: 1.9.20
- Gradle: 8.2
