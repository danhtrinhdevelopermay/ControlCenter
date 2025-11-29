# Control Center Android App

## Overview
The Control Center Android App is a Kotlin-based application designed to provide quick access and control over various Android system functions like WiFi, Bluetooth, flashlight, and more, mimicking the MIUI Control Center UI/UX. It aims to streamline user interaction with core phone features, offering a fast and intuitive interface. The project utilizes Shizuku for privileged system operations, enabling advanced controls without requiring root access. Key capabilities include toggling system settings, media playback control, brightness and volume adjustments, and customizable app shortcuts. Recent enhancements include comprehensive WiFi and Bluetooth scanning and connection features, and a dedicated Notification Center, all designed with a frosted glass blur effect for a premium user experience.

## User Preferences
I prefer simple language and clear explanations. I want an iterative development process, where features are built and reviewed incrementally. Please ask for my approval before implementing any major architectural changes or introducing new significant dependencies. I prioritize a clean, maintainable codebase and efficient solutions. Ensure the UI/UX design strictly adheres to the MIUI Control Center style. Do not make changes to the file `ShizukuHelper.kt` unless absolutely necessary and after explicit approval.

## System Architecture
The application is built using Kotlin for Android, targeting SDK 34 (Min SDK 31) and leveraging View Binding for UI interactions. The core architecture relies on the Shizuku API for executing privileged system commands without requiring root, ensuring broad compatibility and enhanced functionality.

### UI/UX Decisions
The UI is heavily inspired by the MIUI Control Center, featuring:
- **Frosted Glass Blur Effect**: Achieved using `FLAG_BLUR_BEHIND` with dynamic `blurBehindRadius` for a modern aesthetic.
- **Component Layout**: A header displaying date/time, user info, signal, and battery. Large rectangular toggles for WiFi and Cellular Data. A media control card. Vertical sliders for brightness and volume. Eight circular buttons for various functions (Bluetooth, Notification, Flashlight, Rotation, Camera, Screen Mirror, Video, Location). A central grid button and an "Edit" text button for app shortcuts.
- **Theming**: Custom drawables (`miui_toggle_background.xml`, `miui_circle_button.xml`, `miui_slider_background.xml`, `miui_media_background.xml`) ensure a consistent MIUI look.
- **Notification Center**: Accessed by swiping down from the left corner, featuring backdrop blur, app icons, titles, content, and dynamic time display.

### Technical Implementations
- **System Control**: `SystemControlHelper.kt` and `ShizukuHelper.kt` manage system-level operations (WiFi, Bluetooth, data, flashlight, rotation lock) using Shizuku commands.
- **Media Control**: `MediaControlHelper.kt` and `MediaNotificationListener.kt` handle media playback controls (Play/Pause, Next, Previous) and display current track information (title, artist, album art) by listening to media notifications.
- **Brightness/Volume Sliders**: Utilize `Settings.System.SCREEN_BRIGHTNESS` (requires `WRITE_SETTINGS` permission) and `AudioManager.STREAM_MUSIC` respectively, with intuitive vertical touch handling.
- **App Shortcuts**: Managed by `AppShortcutManager.kt` and `AppPickerActivity.kt`, allowing users to select up to 8 favorite apps for quick access, requiring `QUERY_ALL_PACKAGES` permission.
- **WiFi Scanning & Connection**: Implemented via `WiFiScannerHelper.kt`, prioritizing Shizuku shell commands (`cmd wifi list-scan-results`, `cmd wifi connect-network`) to overcome Android 10+ throttling limitations. Fallback to `WifiManager` if Shizuku is unavailable.
- **Bluetooth Scanning & Connection**: Implemented via `ShizukuHelper.kt`, using `dumpsys bluetooth_manager` commands to list and manage paired/connected devices.
- **Notification Listener**: `NotificationCenterService.kt` and `MediaNotificationListener.kt` utilize `BIND_NOTIFICATION_LISTENER_SERVICE` to intercept and display system and media notifications with a blurred background.

### Feature Specifications
- **Core Toggles**: WiFi, Cellular Data, Bluetooth, Flashlight, Rotation Lock.
- **Direct Actions**: Screen Mirror, Camera.
- **Media Controls**: Play/Pause, Next, Previous, displaying media metadata.
- **Sliders**: Brightness and Volume.
- **Customizable App Shortcuts**: Up to 8 user-defined app shortcuts.
- **WiFi Management**: Scan, list, and connect to WiFi networks (supports WPA2/3, WEP, open).
- **Bluetooth Management**: List, connect, and disconnect paired/available Bluetooth devices.
- **Notification Center**: Displays system and app notifications with interactive elements.

## External Dependencies
- **Shizuku API**: `dev.rikka.shizuku:api:13.1.5` and `dev.rikka.shizuku:provider:13.1.5` for executing privileged system commands.