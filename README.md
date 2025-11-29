# iOS-Style Control Center for Android

An Android application that replicates the iOS Control Center experience with blur effects and swipe gesture.

## Features

- **iOS-style Control Center UI** - Clean, modern design matching iOS aesthetics
- **Blur Effect** - Real-time background blur using Android 12+ RenderEffect API
- **Swipe Gesture** - Open by swiping down from the top-right corner
- **Spring Animations** - Smooth, bouncy animations for natural feel
- **Toggle Controls** - WiFi, Bluetooth, Airplane Mode, Cellular Data, Flashlight, DND, Rotation Lock
- **Sliders** - Brightness and Volume control sliders

## Requirements

- Android 12 (API 31) or higher
- Kotlin 1.9+
- JDK 17

## Building the APK

### Using GitHub Actions (Recommended)

1. Push this repository to GitHub
2. Go to the **Actions** tab in your repository
3. The workflow will automatically build the APK on push to main/master
4. Download the APK from the **Artifacts** section

### Manual Build

```bash
# Clone the repository
git clone <your-repo-url>
cd ControlCenter

# Build debug APK
./gradlew assembleDebug

# Build release APK
./gradlew assembleRelease

# APK location
# Debug: app/build/outputs/apk/debug/app-debug.apk
# Release: app/build/outputs/apk/release/app-release-unsigned.apk
```

## Project Structure

```
ControlCenter/
├── app/
│   ├── src/main/
│   │   ├── java/com/example/controlcenter/
│   │   │   └── MainActivity.kt          # Main activity with gesture and blur logic
│   │   ├── res/
│   │   │   ├── layout/
│   │   │   │   ├── activity_main.xml     # Main layout
│   │   │   │   └── control_center_panel.xml  # Control center panel
│   │   │   ├── drawable/                 # Icons and backgrounds
│   │   │   └── values/                   # Colors, strings, themes
│   │   └── AndroidManifest.xml
│   └── build.gradle.kts
├── .github/workflows/
│   └── build-apk.yml                     # GitHub Actions workflow
├── build.gradle.kts
├── settings.gradle.kts
└── gradle/wrapper/
```

## How It Works

### Blur Effect
Uses Android 12's `RenderEffect.createBlurEffect()` API for hardware-accelerated blur:

```kotlin
val blurEffect = RenderEffect.createBlurEffect(
    blurRadius,
    blurRadius,
    Shader.TileMode.CLAMP
)
backgroundImage.setRenderEffect(blurEffect)
```

### Gesture Detection
- Touch area in top-right corner detects swipe down
- Smooth dragging with progress-based blur intensity
- Spring animations for natural open/close behavior

### Controls
Toggle buttons for system controls with active/inactive states and press animations.

## Customization

### Change Blur Intensity
In `MainActivity.kt`, modify `maxBlurRadius`:
```kotlin
private val maxBlurRadius = 25f  // Increase for more blur
```

### Modify Colors
Edit `app/src/main/res/values/colors.xml`:
```xml
<color name="control_item_active">#FF007AFF</color>  <!-- Active button color -->
<color name="control_item_inactive">#4DFFFFFF</color>  <!-- Inactive button color -->
```

## License

MIT License
