# 🚗 Phạt Nguội Checker

Ứng dụng Android tự động kiểm tra phạt nguội, chạy nền liên tục và thông báo khi phát hiện vi phạm chưa xử lý.

## ✨ Tính năng

- **Chạy nền (Background Service)** — tự động chạy ngay cả khi tắt giao diện
- **Auto-start sau khi khởi động máy** — tự bật lại khi restart điện thoại
- **Danh sách tối đa 5 biển số** — ô tô và xe máy
- **Tần suất kiểm tra tùy chọn** — 1h / 3h / 6h / 12h / 24h
- **Thông báo âm thanh** khi phát hiện vi phạm chưa xử lý
- **Xem chi tiết vi phạm** trong ứng dụng: hành vi, thời gian, địa điểm, đơn vị xử lý
- **Badge "MỚI"** trên kết quả khi phát hiện vi phạm mới

## 📱 Cài đặt APK từ GitHub Actions

1. Vào tab **Actions** trên trang GitHub repository này
2. Chọn workflow run mới nhất
3. Tải file `PhatNguoiChecker-debug-xxx.apk` từ mục **Artifacts**
4. Cài đặt trên Android (cần bật *Cài đặt từ nguồn không xác định*)

## 🛠️ Build thủ công

```bash
git clone <repo-url>
cd PhatNguoiChecker
chmod +x gradlew
./gradlew assembleDebug
# APK ở: app/build/outputs/apk/debug/app-debug.apk
```

Yêu cầu: JDK 17+, Android SDK

## 🚀 Deploy lên GitHub

```bash
git init
git add .
git commit -m "Initial commit"
git remote add origin https://github.com/<username>/PhatNguoiChecker.git
git push -u origin main
```

GitHub Actions sẽ tự động build APK sau mỗi lần push.

## 📋 Hướng dẫn sử dụng

### Thêm biển số
1. Mở ứng dụng → nhấn **+ Thêm biển số**
2. Nhập biển số (VD: `73A05550`) và chọn loại xe
3. Tối đa 5 biển số

### Bắt đầu giám sát tự động
1. Chọn **Tần suất kiểm tra** (khuyên dùng: 1h hoặc 6h)
2. Nhấn **▶ Bắt đầu** — ứng dụng chạy nền, tự khởi động lại sau khi reboot
3. Nhấn **⚡ Tra ngay** để kiểm tra ngay lập tức

### Khi có vi phạm
- **Thông báo ting ting** xuất hiện trên thanh thông báo
- Mở ứng dụng → phần **Kết quả gần nhất** có badge **MỚI** đỏ
- Nhấn vào kết quả để xem **chi tiết đầy đủ** từng vi phạm

### Lưu ý quyền
- **Thông báo**: cần cấp quyền POST_NOTIFICATIONS (Android 13+)
- **Tắt tối ưu pin**: vào Cài đặt → Pin → Ứng dụng → PhatNguoiChecker → Không hạn chế

## 🔧 Cấu trúc dự án

```
app/src/main/
├── java/com/phatnguoi/checker/
│   ├── data/
│   │   ├── PhatNguoiApi.kt    
│   │   └── AppRepository.kt    # Lưu trữ dữ liệu (SharedPreferences)
│   ├── model/
│   │   └── Models.kt           # Data classes
│   ├── service/
│   │   ├── CheckService.kt     # Foreground service chạy nền
│   │   └── Receivers.kt        # BootReceiver + AlarmReceiver
│   └── ui/
│       ├── MainActivity.kt
│       ├── MainViewModel.kt
│       ├── ViolationDetailActivity.kt
│       ├── Adapters.kt
│       └── SplashActivity.kt
└── res/
    ├── layout/                  # XML layouts
    ├── drawable/                # Icons & backgrounds
    └── values/                  # Colors, strings, themes
```


Dữ liệu trả về là HTML, app tự parse thông tin vi phạm từ HTML response.

## ⚠️ Lưu ý

- Dữ liệu, độ chính xác phụ thuộc vào nguồn dữ liệu của cơ quan chức năng
- Kiểm tra quá thường xuyên có thể bị block IP — khuyên dùng tần suất ≥ 1 giờ
- Ứng dụng này chỉ mang tính tham khảo, không thay thế tra cứu chính thức
