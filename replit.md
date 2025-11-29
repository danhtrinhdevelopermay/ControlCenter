# Control Center Android App

## Thông tin dự án
- **Tên dự án**: Control Center
- **Ngôn ngữ**: Kotlin + Android
- **Mô tả**: App điều khiển nhanh các chức năng hệ thống Android như WiFi, Bluetooth, đèn pin, v.v.
- **Ngày tạo**: 2025-11-29
- **Phong cách UI**: iOS 18 Control Center

## Cấu trúc dự án
- Android app sử dụng Shizuku để điều khiển các chức năng hệ thống
- Sử dụng Kotlin với View Binding
- Target SDK: 34, Min SDK: 31

## Vấn đề đã sửa

### Lỗi Build APK (2025-11-29)
**Các lỗi đã sửa:**
1. ✅ ShizukuHelper.kt - `Shizuku.newProcess()` là private
   - **Giải pháp**: Sử dụng reflection để access private method
2. ✅ SystemControlHelper.kt - `ACTION_ZEN_MODE_SETTINGS` không tồn tại
   - **Giải pháp**: Thay bằng `ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS`

### Vấn đề hiện tại
- **Triệu chứng**: Đã cấp quyền Shizuku nhưng chỉ có đèn pin và camera hoạt động
- **Nguyên nhân**: Shell commands cần quyền Shizuku để thực thi
- **Giải pháp đã áp dụng**: ✅ Sử dụng reflection để gọi `Shizuku.newProcess()`

### UI Improvements iOS 18 Style (2025-11-29)
**Cập nhật giao diện:**
1. ✅ **Background Colors** - Cập nhật từ `#662C2C2E` → `#66414145` (iOS frosted glass effect)
2. ✅ **Corner Radius** - Tăng từ 22dp → 26dp (chuẩn iOS 18)
3. ✅ **Control Item Background** - Cập nhật từ `#662C2C2E` → `#80535358` (opacity tốt hơn)
4. ✅ **Active State** - Giảm opacity từ `#FFFFFF` → `#E6FFFFFF` (subtle white)
5. ✅ **Circle Buttons** - Background từ `#4D2C2C2E` → `#66414145`

**Files đã thay đổi:**
- `ios_widget_background.xml` - Widget container background
- `ios_connectivity_widget.xml` - WiFi/Bluetooth group background
- `ios_now_playing_widget.xml` - Music player background
- `control_item_background.xml` - Small button backgrounds
- `control_item_background_active.xml` - Active state
- `ios_circle_button.xml` - Bottom row buttons
- `ios_circle_button_active.xml` - Active state

## Dependencies
```kotlin
implementation("dev.rikka.shizuku:api:13.1.5")
implementation("dev.rikka.shizuku:provider:13.1.5")
```

## Permissions
- `moe.shizuku.manager.permission.API_V23` - Shizuku API permission
- `WRITE_SETTINGS`, `ACCESS_NOTIFICATION_POLICY` - Các quyền hệ thống
- Bluetooth, WiFi, Camera permissions

## Chức năng Media Player (2025-11-29)
**Đã thêm:**
1. ✅ **Media Control Buttons** - Play/Pause, Next, Previous
2. ✅ **Media State Detection** - Phát hiện nhạc đang phát qua AudioManager
3. ✅ **Media Commands** - Send media key events qua AudioManager.dispatchMediaKeyEvent()
4. ✅ **UI Update** - TextView hiển thị "Playing" khi phát nhạc, "Not Playing" khi không

**Files đã thêm/cập nhật:**
- `MediaControlHelper.kt` - Helper class để điều khiển media
- `ControlCenterService.kt` - Thêm media button click listeners
- `control_center_panel.xml` - Thêm ID cho musicTitle TextView

**Cách hoạt động:**
- Sử dụng `AudioManager.dispatchMediaKeyEvent()` để send media keys
- Không cần Notification Listener permission
- Hoạt động với mọi music app (Spotify, YouTube Music, Zalo, v.v.)

## Android Layout iOS 17 Style (2025-11-29)
**Cập nhật layout giống iOS 17 Control Center:**

**Các thành phần UI đã cập nhật:**
1. ✅ Connectivity widget (2x2 grid: Airplane, Cellular, WiFi, Bluetooth)
2. ✅ Now Playing widget với media controls và AirPlay button
3. ✅ Orientation Lock button (active state với red icon)
4. ✅ Screen Mirror button
5. ✅ Focus mode widget với moon icon
6. ✅ Brightness slider (tall pill shape)
7. ✅ Volume slider (tall pill shape với mute icon)
8. ✅ Bottom controls: Flashlight, Calculator, Screen Recording, Display
9. ✅ Extra controls: Hearing, Timer, Battery, Accessibility

**Files đã thay đổi:**
- `control_center_panel.xml` - Layout hoàn toàn mới giống iOS 17
- `ios_widget_background.xml` - Dark translucent background
- `ios_widget_background_active.xml` - White active state
- `ios_small_button.xml` - Small square button background
- `ios_small_button_active.xml` - Active state với white background
- `ios_slider_background.xml` - Tall slider background
- `ios_slider_fill.xml` - White slider fill
- `ios_focus_circle.xml` - Purple circle cho Focus icon
- `ios_home_indicator.xml` - Home indicator bar

**Icons mới:**
- `ic_volume_mute.xml` - Volume muted icon
- `ic_screen_recording.xml` - Screen recording icon
- `ic_display.xml` - Display/eye icon
- `ic_hearing.xml` - Hearing icon
- `ic_battery.xml` - Battery icon
- `ic_accessibility.xml` - Accessibility icon

## Ghi chú kỹ thuật
- Shizuku `newProcess()` đã bị deprecated nhưng vẫn có thể dùng qua reflection
- App cần cấp quyền Shizuku và các quyền runtime khác để hoạt động đầy đủ
- UI design theo iOS 18 Control Center với frosted glass effect
- Media controls hoạt động qua system media key events, không cần thêm permission
