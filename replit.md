# Control Center - iOS Style Android App (System Overlay)

## Overview
This is an Android Kotlin application that provides a system-wide Control Center overlay similar to iOS. Key features:
- **System Overlay** - Displays on top of all apps
- **Swipe Gesture** - Open by swiping down from top-right corner
- **Accessibility Service** - Detects gestures system-wide
- **Foreground Service** - Runs continuously in background
- **GitHub Actions** - Automated APK building

## Project Type
**Android Native Application** - Cannot run on Replit (requires Android device). Build using GitHub Actions.

## Architecture

### Components
1. **MainActivity** - Setup UI, permission handling
2. **GestureAccessibilityService** - Gesture detection via Accessibility API
3. **ControlCenterService** - Foreground service managing floating overlay
4. **BootReceiver** - Auto-start on device boot

### Flow
```
User swipe → GestureAccessibilityService detects → 
ControlCenterService shows overlay → User interacts → 
Dismiss gesture → Overlay hides
```

### Structure
```
app/src/main/
├── java/com/example/controlcenter/
│   ├── MainActivity.kt
│   ├── GestureAccessibilityService.kt
│   ├── ControlCenterService.kt
│   └── BootReceiver.kt
├── res/
│   ├── layout/
│   ├── drawable/
│   ├── xml/accessibility_service_config.xml
│   └── values/
└── AndroidManifest.xml
```

## Permissions Required
- SYSTEM_ALERT_WINDOW (overlay)
- FOREGROUND_SERVICE (background running)
- BIND_ACCESSIBILITY_SERVICE (gesture detection)
- VIBRATE (haptic feedback)

## How to Build
1. Push to GitHub repository
2. Go to Actions tab
3. Download APK from Artifacts

## Recent Changes
- Nov 29, 2025: Improved touch gestures with velocity tracking for smoother show/hide animations
- Nov 29, 2025: Redesigned UI to match iOS Control Center exactly with connectivity widget, now playing, sliders, and bottom controls
- Nov 29, 2025: Added backdrop blur effect using FLAG_BLUR_BEHIND and blurBehindRadius (Android 12+)
- Nov 29, 2025: Converted to system-wide overlay with accessibility service

## Technical Notes
- Minimum SDK: 31 (Android 12)
- Target SDK: 34
- Uses WindowManager for overlay
- Accessibility service for gesture detection
- Spring animations via DynamicAnimation
- Backdrop blur using FLAG_BLUR_BEHIND (Android 12+) with dynamic blur radius animation
- iOS-style UI with rounded widgets, circular buttons, and vertical sliders
