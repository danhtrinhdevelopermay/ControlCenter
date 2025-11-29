# iOS-Style Control Center for Android

A floating Control Center overlay that works system-wide, just like iOS! Swipe down from the top-right corner of your screen to access quick controls from any app.

## Features

- **System-wide Overlay** - Works on top of any app
- **Swipe Gesture** - Open by swiping down from the top-right corner
- **iOS-style Design** - Clean, modern UI matching iOS aesthetics
- **Quick Controls** - WiFi, Bluetooth, Airplane Mode, Flashlight, DND, Rotation Lock
- **Smooth Animations** - Spring physics for natural feel
- **Haptic Feedback** - Vibration on interactions

## Requirements

- Android 12 (API 31) or higher
- Overlay permission
- Accessibility service permission

## How It Works

1. **Accessibility Service** - Detects swipe gestures from the top-right corner
2. **Overlay Permission** - Allows displaying Control Center over other apps
3. **Foreground Service** - Keeps the service running in background

## Setup Instructions

1. Install the APK on your Android device
2. Open the app and grant **Overlay Permission**
3. Enable the **Accessibility Service** for Control Center
4. Done! Swipe down from the top-right corner of any screen

## Building the APK

### Using GitHub Actions (Recommended)

1. Push this repository to GitHub
2. Go to the **Actions** tab
3. Wait for the build to complete
4. Download APK from **Artifacts**

### Manual Build

```bash
./gradlew assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
```

## Project Structure

```
app/src/main/java/com/example/controlcenter/
├── MainActivity.kt              # Setup UI and permission handling
├── GestureAccessibilityService.kt  # Detects swipe gestures
├── ControlCenterService.kt      # Manages floating overlay
└── BootReceiver.kt              # Auto-start on device boot

app/src/main/res/
├── layout/
│   ├── activity_main.xml        # Setup screen
│   └── control_center_panel.xml # Control Center UI
├── xml/
│   └── accessibility_service_config.xml
└── drawable/                    # Icons and backgrounds
```

## Permissions Explained

| Permission | Purpose |
|------------|---------|
| SYSTEM_ALERT_WINDOW | Display overlay on other apps |
| FOREGROUND_SERVICE | Keep service running |
| BIND_ACCESSIBILITY_SERVICE | Detect swipe gestures |
| VIBRATE | Haptic feedback |

## Security Note

This app requires Accessibility Service permission to detect gestures. The service ONLY monitors touch events in a small area at the top-right corner of the screen and does not access any content from other apps.

## License

MIT License
