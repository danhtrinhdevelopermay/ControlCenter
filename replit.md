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
- `ACCESS_FINE_LOCATION` - Cần thiết để lấy tên WiFi (SSID) trên Android 10+
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

## Chức năng các nút (Cập nhật 2025-11-29)

### Đã hoạt động:
- ✅ WiFi toggle - Bật/tắt WiFi qua Shizuku
- ✅ Cellular data toggle - Bật/tắt dữ liệu di động qua Shizuku
- ✅ Bluetooth - Bật/tắt Bluetooth qua Shizuku
- ✅ Flashlight - Bật/tắt đèn pin
- ✅ Rotation lock - Khóa/mở khóa xoay màn hình
- ✅ Screen Mirror - Chức năng phản chiếu màn hình
- ✅ Camera - Mở camera
- ✅ Media controls - Play/Pause, Next, Previous
- ✅ Brightness slider - Điều chỉnh độ sáng màn hình
- ✅ Volume slider - Điều chỉnh âm lượng
- ✅ App Shortcuts - Thêm phím tắt ứng dụng tùy chọn (tối đa 8 ứng dụng)
- ✅ Edit button - Mở màn hình chọn ứng dụng

### Placeholder (chưa implement):
- 🔲 Notification - Chế độ thông báo (hiện tại chỉ có animation)
- 🔲 Video - Chức năng video (hiện tại chỉ có animation)
- 🔲 Location - GPS/định vị (hiện tại chỉ có animation)
- 🔲 Grid button - Menu grid (hiện tại chỉ có animation)

## App Shortcuts Feature (Cập nhật 2025-11-29)

### Mô tả:
Cho phép người dùng thêm tối đa 8 phím tắt ứng dụng vào Control Center.

### Files mới:
- `AppShortcutManager.kt` - Quản lý danh sách ứng dụng và shortcuts đã lưu
- `AppPickerActivity.kt` - Activity chọn ứng dụng với tính năng tìm kiếm
- `activity_app_picker.xml` - Layout cho màn hình chọn ứng dụng
- `item_app_list.xml` - Layout cho item ứng dụng trong danh sách

### Cách sử dụng:
1. Nhấn nút "Sửa" trong Control Center
2. Chọn các ứng dụng muốn thêm vào phím tắt (tối đa 8 ứng dụng)
3. Quay lại Control Center, các phím tắt sẽ hiển thị
4. Nhấn vào icon ứng dụng để mở nhanh

### Permissions:
- `QUERY_ALL_PACKAGES` - Cần thiết để lấy danh sách ứng dụng đã cài đặt

## Lịch sử thay đổi
- **2025-11-29**: Redesign từ iOS 17 sang MIUI Control Center
- **2025-11-29**: Sửa lỗi build - xóa tham chiếu đến airplaneButton từ layout cũ
- **2025-11-29**: Thêm hiển thị tên WiFi (SSID) khi kết nối thành công
- **2025-11-29**: Thêm tính năng App Shortcuts - cho phép thêm phím tắt ứng dụng tùy chọn
- **2025-11-29**: Navigation bar và status bar hiện với background trong suốt khi Control Center mở
- **2025-11-29**: Sửa lỗi brightness và volume slider - thêm touch handling và đồng bộ với giá trị hệ thống
- **2025-11-29**: Thêm tính năng Media Info - hiển thị thông tin bài hát đang phát (tên, nghệ sĩ, album art)
- **2025-11-29**: Thêm tính năng WiFi Scanning - quét và kết nối mạng WiFi trực tiếp từ Control Center
- **2025-11-29**: Sửa lỗi WiFi scanning với Shizuku - cải thiện parsing để hỗ trợ nhiều định dạng output
- **2025-11-29**: Thêm tính năng Bluetooth Scanning - quét và kết nối thiết bị Bluetooth từ Control Center

## WiFi Scanning Feature (Cập nhật 2025-11-29)

### Mô tả:
Cho phép người dùng quét danh sách mạng WiFi khả dụng và kết nối trực tiếp từ Control Center mà không cần vào Settings.

### Files:
- `WiFiScannerHelper.kt` - Helper quét và kết nối mạng WiFi (sử dụng Shizuku hoặc phương thức tiêu chuẩn)
- `WiFiNetworkAdapter.kt` - Adapter hiển thị danh sách mạng WiFi
- `ShizukuHelper.kt` - Thêm chức năng quét WiFi qua Shizuku shell commands
- `dialog_wifi_list.xml` - Layout popup danh sách mạng WiFi
- `dialog_wifi_password.xml` - Layout popup nhập mật khẩu
- `item_wifi_network.xml` - Layout item mạng WiFi trong danh sách
- `ic_lock.xml` - Icon khóa cho mạng bảo mật
- `ic_refresh.xml` - Icon làm mới danh sách
- `ic_check.xml` - Icon đánh dấu mạng đang kết nối

### Cách sử dụng:
1. **Nhấn giữ** nút WiFi trong Control Center
2. Popup hiển thị danh sách mạng WiFi khả dụng
3. Nhấn vào mạng muốn kết nối
4. Nếu mạng có mật khẩu, nhập mật khẩu và nhấn "Kết nối"
5. Đợi kết nối hoàn tất

### Tính năng:
- Hiển thị tín hiệu WiFi (mạnh/yếu)
- Icon khóa cho mạng bảo mật
- Đánh dấu mạng đang kết nối
- Nút làm mới danh sách
- Hỗ trợ WPA2, WPA3, WEP và mạng mở
- Hiển thị lỗi nếu kết nối thất bại
- **Sử dụng Shizuku để quét WiFi** - Khắc phục hạn chế throttling trên Android 10+

### Permissions cần thiết:
- `ACCESS_WIFI_STATE` - Đọc trạng thái WiFi
- `CHANGE_WIFI_STATE` - Thay đổi trạng thái WiFi
- `ACCESS_FINE_LOCATION` - Quét mạng WiFi (dự phòng khi Shizuku không khả dụng)
- `ACCESS_COARSE_LOCATION` - Hỗ trợ quét mạng WiFi
- `ACCESS_NETWORK_STATE` - Kiểm tra trạng thái mạng
- `moe.shizuku.manager.permission.API_V23` - Shizuku API (ưu tiên)

### Lưu ý kỹ thuật:
- **Shizuku (Ưu tiên)**: Sử dụng `cmd wifi list-scan-results` hoặc `dumpsys wifi` để lấy danh sách mạng WiFi, không bị giới hạn throttling
- **Fallback**: Nếu Shizuku không khả dụng, sử dụng WifiManager.startScan() (có hạn chế trên Android 10+)
- Android 10+: WifiManager.startScan() bị giới hạn 4 lần quét mỗi 2 phút
- Kết nối WiFi: Ưu tiên sử dụng `cmd wifi connect-network` qua Shizuku
- Mạng doanh nghiệp (EAP) không được hỗ trợ
- WPA3 chỉ hỗ trợ trên Android 11+

### Cách Shizuku quét WiFi:
1. Thực thi `cmd wifi start-scan` để bắt đầu quét
2. Đợi 2 giây để quét hoàn tất
3. Thực thi `cmd wifi list-scan-results` để lấy kết quả
4. Nếu không có kết quả, thử `dumpsys wifi | grep -A 50 'Latest scan results'`
5. Cuối cùng thử `wpa_cli -i wlan0 scan_results` (cho một số thiết bị)

## Bluetooth Scanning Feature (Cập nhật 2025-11-29)

### Mô tả:
Cho phép người dùng xem danh sách thiết bị Bluetooth đã ghép đôi và kết nối/ngắt kết nối trực tiếp từ Control Center.

### Files:
- `ShizukuHelper.kt` - Thêm chức năng quét và kết nối Bluetooth qua Shizuku shell commands
- `BluetoothDeviceAdapter.kt` - Adapter hiển thị danh sách thiết bị Bluetooth
- `dialog_bluetooth_list.xml` - Layout popup danh sách thiết bị Bluetooth
- `item_bluetooth_device.xml` - Layout item thiết bị Bluetooth trong danh sách

### Cách sử dụng:
1. **Nhấn giữ** nút Bluetooth trong Control Center
2. Popup hiển thị danh sách thiết bị Bluetooth đã ghép đôi và khả dụng
3. Nhấn vào thiết bị để kết nối hoặc ngắt kết nối
4. Nhấn nút làm mới để quét lại danh sách

### Tính năng:
- Hiển thị danh sách thiết bị đã ghép đôi
- Hiển thị trạng thái kết nối (Đã kết nối / Đã ghép đôi / Khả dụng)
- Icon khác biệt cho thiết bị đã kết nối vs chưa kết nối
- Kết nối/ngắt kết nối thiết bị bằng cách nhấn
- Nút làm mới danh sách
- Sắp xếp: Thiết bị đã kết nối > Đã ghép đôi > Khả dụng

### Cách Shizuku quét Bluetooth:
1. Thực thi `dumpsys bluetooth_manager | grep -A 20 'Bonded devices'` để lấy thiết bị đã ghép đôi
2. Thực thi `dumpsys bluetooth_manager | grep -A 10 'Connected'` để kiểm tra thiết bị đang kết nối
3. Phân tích output để lấy tên thiết bị và địa chỉ MAC
4. Nếu cần, thử `cmd bluetooth enable-scan` để quét thiết bị khả dụng

### Kết nối Bluetooth qua Shizuku:
- Sử dụng `cmd bluetooth connect <MAC_ADDRESS>`
- Fallback: `btmgmt connect <MAC_ADDRESS>`
- Fallback cuối: Mở intent ghép đôi thiết bị

## Media Notification Listener Feature

### Mô tả:
Hiển thị thông tin bài hát đang phát trong Control Center (tên bài, nghệ sĩ, album art).

### Files mới:
- `MediaNotificationListener.kt` - Service lắng nghe thông báo media để lấy thông tin bài hát

### Cách hoạt động:
1. App yêu cầu quyền Notification Access
2. MediaNotificationListener kết nối với MediaSessionManager
3. Khi có bài hát đang phát, lấy thông tin từ MediaMetadata
4. Hiển thị tên bài, nghệ sĩ và album art trong Control Center

### Permissions:
- `BIND_NOTIFICATION_LISTENER_SERVICE` - Cần thiết để lắng nghe thông báo media

### UI Updates:
- `musicTitle` - Hiển thị tên bài hát
- `musicArtist` - Hiển thị tên nghệ sĩ
- `albumArtView` - Hiển thị ảnh album
- `playButton` - Đổi icon play/pause theo trạng thái

## Ghi chú về Brightness/Volume Sliders

### Brightness Slider:
- Sử dụng `Settings.System.SCREEN_BRIGHTNESS` để đọc/ghi độ sáng
- Yêu cầu quyền `WRITE_SETTINGS` để thay đổi độ sáng
- Phạm vi: 0-255

### Volume Slider:
- Sử dụng `AudioManager.STREAM_MUSIC` để điều khiển âm lượng media
- Không cần quyền đặc biệt
- Phạm vi: 0 đến maxVolume (thường là 15)

### Touch Handling:
- Vuốt từ dưới lên để tăng giá trị
- Vuốt từ trên xuống để giảm giá trị
- Visual feedback cập nhật real-time khi vuốt
