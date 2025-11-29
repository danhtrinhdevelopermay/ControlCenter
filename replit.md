# Control Center Android App

## Thông tin dự án
- **Tên dự án**: Control Center
- **Ngôn ngữ**: Kotlin + Android
- **Mô tả**: App điều khiển nhanh các chức năng hệ thống Android như WiFi, Bluetooth, đèn pin, v.v.
- **Ngày tạo**: 2025-11-29
- **Phong cách UI**: iOS 17 Control Center

## Cấu trúc dự án
- Android app sử dụng Shizuku để điều khiển các chức năng hệ thống
- Sử dụng Kotlin với View Binding
- Target SDK: 34, Min SDK: 31

## Build
```bash
./gradlew assembleDebug
# APK output: app/build/outputs/apk/debug/app-debug.apk
```

## Dependencies
```kotlin
implementation("dev.rikka.shizuku:api:13.1.5")
implementation("dev.rikka.shizuku:provider:13.1.5")
```

## Permissions
- `moe.shizuku.manager.permission.API_V23` - Shizuku API permission
- `WRITE_SETTINGS`, `ACCESS_NOTIFICATION_POLICY` - Các quyền hệ thống
- Bluetooth, WiFi, Camera permissions

## Layout iOS 17 Style (2025-11-29)

### Các thành phần UI:
| Component | Mô tả |
|-----------|-------|
| Connectivity Widget | Grid 2x2: Airplane, Cellular, WiFi, Bluetooth |
| Now Playing Widget | Media controls với Play/Pause, Next, Previous, AirPlay |
| Orientation Lock | Khóa xoay màn hình |
| Screen Mirror | Phản chiếu màn hình |
| Focus Mode | Chế độ tập trung với moon icon |
| Brightness Slider | Thanh trượt độ sáng dọc |
| Volume Slider | Thanh trượt âm lượng dọc |
| Bottom Controls | Flashlight, Calculator, Screen Recording, Display |
| Extra Controls | Hearing, Timer, Battery, Accessibility |

### Button IDs mới:
- `calculatorButton` / `calculatorIcon`
- `recordingButton` / `recordingIcon`
- `displayButton` / `displayIcon`
- `hearingButton` / `hearingIcon`
- `batteryButton` / `batteryIcon`
- `accessibilityButton` / `accessibilityIcon`

### Files chính:
- `app/src/main/res/layout/control_center_panel.xml` - Layout iOS 17
- `app/src/main/java/com/example/controlcenter/ControlCenterService.kt` - Service xử lý UI
- `app/src/main/java/com/example/controlcenter/SystemControlHelper.kt` - Helper điều khiển hệ thống
- `app/src/main/java/com/example/controlcenter/MediaControlHelper.kt` - Helper điều khiển media

## Ghi chú kỹ thuật
- Shizuku `newProcess()` cần reflection để access
- App cần cấp quyền Shizuku và các quyền runtime khác để hoạt động đầy đủ
- UI design theo iOS 17 Control Center với frosted glass effect
- Media controls hoạt động qua system media key events
