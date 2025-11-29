# Control Center Android App

## Thông tin dự án
- **Tên dự án**: Control Center
- **Ngôn ngữ**: Kotlin + Android
- **Mô tả**: App điều khiển nhanh các chức năng hệ thống Android như WiFi, Bluetooth, đèn pin, v.v.
- **Ngày tạo**: 2025-11-29
- **Phong cách UI**: MIUI Control Center

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

## Layout MIUI Style (Cập nhật 2025-11-29)

### Các thành phần UI:
| Component | Mô tả |
|-----------|-------|
| Header | Ngày/giờ, tên người dùng, tín hiệu và pin |
| WiFi Toggle | Nút toggle lớn hình chữ nhật với trạng thái |
| Data Toggle | Nút toggle lớn hình chữ nhật với trạng thái |
| Media Control | Card điều khiển phát nhạc với Play/Pause, Next, Previous |
| Brightness Slider | Thanh trượt độ sáng dọc (bên phải) |
| Volume Slider | Thanh trượt âm lượng dọc (bên phải) |
| Circular Buttons | 8 nút tròn: Bluetooth, Notification, Flashlight, Rotation, Camera, Screen Mirror, Video, Location |
| Grid Button | Nút grid ở giữa dưới cùng |
| Edit Button | Nút "Sửa" dạng text button |

### Button IDs MIUI:
- `wifiButton` / `wifiIcon` - WiFi toggle
- `cellularButton` / `cellularIcon` - Data toggle
- `bluetoothButton` / `bluetoothIcon` - Bluetooth
- `notificationButton` / `notificationIcon` - Thông báo
- `flashlightButton` / `flashlightIcon` - Đèn pin
- `rotationButton` / `rotationIcon` - Xoay màn hình
- `cameraButton` / `cameraIcon` - Camera
- `screenMirrorButton` / `screenMirrorIcon` - Screen Mirror
- `videoButton` / `videoIcon` - Video
- `locationButton` / `locationIcon` - GPS
- `gridButton` / `gridIcon` - Grid menu

### Files chính:
- `app/src/main/res/layout/control_center_panel.xml` - Layout MIUI
- `app/src/main/java/com/example/controlcenter/ControlCenterService.kt` - Service xử lý UI
- `app/src/main/java/com/example/controlcenter/SystemControlHelper.kt` - Helper điều khiển hệ thống
- `app/src/main/java/com/example/controlcenter/MediaControlHelper.kt` - Helper điều khiển media

### MIUI Drawables:
- `miui_toggle_background.xml` - Background cho toggle buttons (WiFi, Data)
- `miui_circle_button.xml` - Background cho circular buttons
- `miui_slider_background.xml` - Background cho sliders
- `miui_media_background.xml` - Background cho media control

## Ghi chú kỹ thuật
- Shizuku `newProcess()` cần reflection để access
- App cần cấp quyền Shizuku và các quyền runtime khác để hoạt động đầy đủ
- UI design theo MIUI Control Center với frosted glass blur effect (giữ nguyên từ version trước)
- Background blur effects: `FLAG_BLUR_BEHIND` với `blurBehindRadius` động theo animation
- Media controls hoạt động qua system media key events
