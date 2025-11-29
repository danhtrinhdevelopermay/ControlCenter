# Control Center Android App

## Thông tin dự án
- **Tên dự án**: Control Center
- **Ngôn ngữ**: Kotlin + Android
- **Mô tả**: App điều khiển nhanh các chức năng hệ thống Android như WiFi, Bluetooth, đèn pin, v.v.
- **Ngày tạo**: 2025-11-29

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
- **Giải pháp đang áp dụng**: Sử dụng reflection để gọi `Shizuku.newProcess()`

## Dependencies
```kotlin
implementation("dev.rikka.shizuku:api:13.1.5")
implementation("dev.rikka.shizuku:provider:13.1.5")
```

## Permissions
- `moe.shizuku.manager.permission.API_V23` - Shizuku API permission
- `WRITE_SETTINGS`, `ACCESS_NOTIFICATION_POLICY` - Các quyền hệ thống
- Bluetooth, WiFi, Camera permissions

## Ghi chú kỹ thuật
- Shizuku `newProcess()` đã bị deprecated nhưng vẫn có thể dùng qua reflection
- App cần cấp quyền Shizuku và các quyền runtime khác để hoạt động đầy đủ
