# Control Center Android App

## Overview
The Control Center Android App is a Kotlin-based application designed to provide quick access and control over various Android system functions like WiFi, Bluetooth, flashlight, and more, mimicking the MIUI Control Center UI/UX. It leverages Shizuku for privileged system operations and targets Android SDK 34 (Min SDK 31). The project aims to offer a highly customizable and efficient way for users to manage their device settings, including features like app shortcuts, advanced WiFi and Bluetooth scanning/connection, and media controls. The application emphasizes a polished user interface with animations and blur effects.

## User Preferences
I prefer detailed explanations. Do not make changes to the folder Z. Do not make changes to the file Y.

## System Architecture
The application is an Android app built with Kotlin, utilizing View Binding for UI interactions. It integrates Shizuku for executing privileged shell commands to control system functions, overcoming Android's API restrictions.

**UI/UX Decisions:**
The UI adheres strictly to the MIUI Control Center design language, featuring:
- A header displaying date, time, user information, signal, and battery status.
- Large rectangular toggle buttons for WiFi and Cellular Data.
- A media control card with play/pause, next, and previous functionalities.
- Vertical sliders for brightness and volume control.
- Eight circular buttons for common functions (Bluetooth, Notification, Flashlight, Rotation, Camera, Screen Mirror, Video, Location).
- A grid button and an edit button for customization.
- Frosted glass blur effects using `RenderEffect.createBlurEffect()` (Android 12+) or fallback alpha dimming for older versions, with dynamic `blurBehindRadius` for animations.
- Custom drawables (`miui_toggle_background.xml`, `miui_circle_button.xml`, `miui_slider_background.xml`, `miui_media_background.xml`) to match MIUI aesthetics.

**Technical Implementations & Feature Specifications:**
- **System Control:** Uses `SystemControlHelper.kt` for managing system settings via Shizuku, including WiFi, Cellular Data, Bluetooth, Flashlight, and Rotation Lock.
- **Media Control:** `MediaControlHelper.kt` manages media playback through system media key events and `MediaNotificationListener.kt` to display current song info (title, artist, album art) by listening to media notifications.
- **Notification Center:** `NotificationCenterService.kt` displays system notifications in a panel. Key improvements (Nov 30, 2025):
    - Added `requestRebind()` method to reconnect the notification listener when disconnected
    - Added `isServiceConnected()` check to verify listener connectivity before fetching notifications
    - `loadNotifications()` now requests rebind and retries if service is disconnected
    - `BootReceiver` now starts both ControlCenterService and NotificationCenterService, and requests rebind for MediaNotificationListener
    - MainActivity's `onResume()` checks and requests rebind if notification access is enabled but service is not connected
    - **Performance optimizations**: Notification loading now runs on background thread (`backgroundExecutor`) to prevent UI lag
    - Removed excessive logging to improve performance
    - **Swipe to dismiss**: Vuốt thông báo sang phải để xóa thông báo đó
    - **Clear all**: Nhấn nút X ở dưới để xóa tất cả thông báo từ hệ thống
    - **RecyclerView migration**: Chuyển từ ScrollView+LinearLayout sang RecyclerView với ViewHolder pattern để cải thiện hiệu suất cuộn đáng kể
- **Brightness & Volume Control:** Sliders directly interact with `Settings.System.SCREEN_BRIGHTNESS` (requiring `WRITE_SETTINGS` permission) and `AudioManager.STREAM_MUSIC`, respectively.
- **App Shortcuts:** Allows users to add up to 8 customizable application shortcuts, managed by `AppShortcutManager.kt` and `AppPickerActivity.kt`. Requires `QUERY_ALL_PACKAGES` permission.
- **Advanced WiFi Scanning & Connection:** `WiFiScannerHelper.kt` enables scanning for available WiFi networks and connecting from within the Control Center. It prioritizes Shizuku for scanning (`cmd wifi list-scan-results`) to bypass Android 10+ throttling, with `WifiManager.startScan()` as a fallback.
- **Bluetooth Device Management:** Enables listing paired and available Bluetooth devices and connecting/disconnecting them. It uses Shizuku commands (`dumpsys bluetooth_manager`) for device information and control.
- **Animations:** Smooth popup animations for WiFi and Bluetooth dialogs, incorporating blur transitions, scale animations (85% to 100% with OvershootInterpolator), and alpha animations. `ValueAnimator` and `AnimatorSet` are used for orchestrating these effects.
- **Appearance Customization:** `AppearanceSettings.kt` stores user-configured colors and opacity (0-100%) for UI components. `AppearanceSettingsActivity.kt` provides a UI to customize:
    - Circle buttons (inactive/active colors and opacity)
    - Toggle buttons like WiFi/Cellular (inactive/active colors and opacity)
    - Media player widget (background color and opacity)
    - Brightness/Volume sliders (track and fill colors with opacity)
    - Panel background (color and opacity)
    Colors are applied dynamically using `GradientDrawable` in `ControlCenterService.kt` via `applyAppearanceSettings()`, `updateButtonState()`, `applyPlayerAppearance()`, and `applySliderAppearance()` methods.
- **Core Files:** `control_center_panel.xml` defines the main MIUI layout. `ControlCenterService.kt` handles UI logic and interactions.

## External Dependencies
- **Shizuku:** `dev.rikka.shizuku:api:13.1.5` and `dev.rikka.shizuku:provider:13.1.5` are used for executing privileged commands and accessing system APIs without root, requiring `moe.shizuku.manager.permission.API_V23`.
- **Android System Services:**
    - `WifiManager` for WiFi control and scanning (fallback).
    - `BluetoothManager` for Bluetooth control.
    - `AudioManager` for volume control.
    - `Settings.System` for brightness control.
    - `MediaSessionManager` for media notification listening.
    - `PackageManager` for querying installed applications for app shortcuts.