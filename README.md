# phonecuibap (Android)
Launcher thật mô phỏng điện thoại cục gạch Nokia: thân máy + bàn phím cố định, chỉ màn hình LCD phía trên thay đổi.

- **Khóa màn hình:** mở app là thấy hình nền che khung hiển thị. Bấm **Menu** rồi bấm **\*** để mở khóa. Tắt màn hình máy sẽ khóa lại.
- **AI giọng nói:** ở màn hình chờ (chưa gõ số) bấm phím mềm phải **AI**, rồi nói lệnh bằng tiếng Việt. Ví dụ: "gọi cho mẹ", "gọi 0912345678", "nhắn tin cho Nam nội dung tối nay họp", "chụp ảnh" / "chụp ảnh tự sướng", "mở thư viện", "mở bài Hãy trao cho anh", "phát nhạc của Sơn Tùng", "đặt báo thức 6 giờ 30", "đặt báo thức sau 20 phút", "mở Zalo / YouTube / Facebook", "mở danh bạ / tin nhắn / lịch", "bật đèn pin", "tăng âm lượng", "bật Bluetooth", "khóa màn hình", "bây giờ là mấy giờ". Gọi và nhắn tin luôn hiện màn hình xác nhận (OK = thực hiện, Về = hủy; ▲▼ đổi người nếu có nhiều người trùng tên). Cần quyền Micro và mạng (dùng dịch vụ nhận dạng giọng nói của Google trên máy). Hướng dẫn các câu lệnh mẫu: ở màn hình chờ bấm phím ← (góc phải trên) 2 lần liên tiếp. Phần hiểu lệnh nằm trong `AiParser.kt` (chạy ngay trên máy, không cần khóa API), thêm câu lệnh mới ở đó.
- **Menu:** Nhật ký (trên cùng), Danh bạ, Tin nhắn, Nhạc, Đồng hồ (báo thức), Lịch (có âm lịch), Ghi âm, Máy tính, Máy ảnh, Thư viện, Rắn săn mồi, Zalo Web, TV, YouTube, Cài đặt
- **Phím tắt ở màn hình chờ (4 phím mũi tên):** ▲ Đồng hồ · ▼ Máy tính · ◀ Lịch · ▶ Máy ảnh. Phím Gọi (xanh lá) khi chưa gõ số mở Nhật ký cuộc gọi. Bấm Về trong các app này sẽ quay lại màn hình chờ.
- **Cài đặt:** Chọn launcher · Cài đặt âm thanh (nhạc chuông, âm lượng, nhạc báo thức) · Wifi · SIM · Chế độ máy bay · Chia sẻ dữ liệu · Độ sáng · Pin · Bộ nhớ · Tài khoản · Lịch bật tắt nguồn · Bluetooth bật/tắt · Ứng dụng gọi · Khôi phục cài đặt gốc (chỉ xóa dữ liệu của app này)
  - **Wifi:** quét và chọn mạng; mạng có khóa thì hiện hộp nhập mật khẩu với bàn phím ảo của máy. Android 10+ không cho app tự bật/tắt wifi nên bấm dòng "Wifi" sẽ mở bảng wifi của hệ thống; Android 11+ lưu mạng qua hộp thoại của hệ thống.
  - **SIM:** mỗi SIM hiện nhà mạng, loại mạng (2G/3G/4G/5G), số điện thoại (nếu nhà mạng có ghi trên SIM). "Gọi bằng" / "Nhắn tin bằng": chọn SIM mà app này dùng khi gọi từ Danh bạ/màn hình chờ và khi gửi tin. SIM dữ liệu và công tắc dữ liệu di động: Android không cho app đổi nên bấm sẽ mở màn hình của hệ thống.
  - **Chế độ máy bay, Chia sẻ dữ liệu (phát wifi):** Android không cho app thường tự bật/tắt, bấm sẽ mở đúng màn hình cài đặt của hệ thống.
  - **Độ sáng:** ◀▶ chỉnh, OK tăng dần; cần cấp quyền "Sửa đổi cài đặt hệ thống".
  - **Pin:** phần trăm pin, thời gian dùng app này trong ngày, nút mở trình tiết kiệm pin của hệ thống.
  - **Bộ nhớ:** tổng / trống / đã dùng của bộ nhớ trong. **Tài khoản:** hiện tài khoản Google (cần cấp quyền).
  - **Lịch bật tắt nguồn:** đặt giờ tắt / giờ bật lặp mỗi ngày. Android không cho app thường tắt/bật nguồn máy nên "tắt" = khóa và tắt màn hình (cần bật Trợ năng phonecuibap, Android 9+), "bật" = bật sáng màn hình.
- **Danh bạ:** đọc danh bạ trong máy; chọn một người (OK / chạm) là **gọi ngay**, không xác nhận. Có tiêu đề và **một ô tìm duy nhất** gộp tìm theo chữ cái đầu và tìm thường: gõ kiểu Nokia (bấm lặp phím số) vài chữ đầu hoặc một phần tên / số, danh sách lọc ngay (không phân biệt hoa thường, dấu; tên bắt đầu bằng chuỗi gõ xếp trước). `#` đổi chữ ↔ số, Xóa (phím mềm phải) xóa từng ký tự, Về khi đang tìm thì xóa ô tìm
- **Tin nhắn:** hộp thư theo hội thoại, đọc, trả lời, soạn mới, **xóa**: `#` ở danh sách hội thoại xóa cả hội thoại, `#` ở danh sách tin / khi đang đọc xóa một tin (có hỏi xác nhận). Vì app không phải ứng dụng SMS mặc định nên "xóa" chỉ ẩn tin khỏi app này, bản gốc vẫn còn trong app Tin nhắn của máy (chọn người nhận bằng ▲▼ hoặc gõ số). Gõ chữ kiểu Nokia (bấm lặp phím số), `*` đổi dấu tiếng Việt (a → à á ả ã ạ ă…), `#` đổi kiểu abc/Abc/ABC/123
- **Nhật ký:** lịch sử cuộc gọi dồn chung một danh sách, mới nhất ở trên: mỗi dòng ghi rõ "Gọi đến:", "Gọi đi:" hoặc "Gọi nhỡ:" (nhỡ gồm cả từ chối). Dòng trên cùng cho biết loại, ngày giờ, thời lượng và số. OK / Gọi = gọi lại ngay. Cần quyền Nhật ký cuộc gọi.
- **Nhạc:** liệt kê nhạc trong bộ nhớ máy và thẻ nhớ (đọc qua thư viện đa phương tiện của Android, thẻ nhớ phải được hệ thống quét), mỗi bài ghi rõ "Máy" hay "Thẻ nhớ". ▲▼ chọn bài, OK phát / tạm dừng, ◀▶ bài trước / sau, `4` `6` tua lùi / tới 10 giây, `5` phát / tạm dừng, `0` dừng, `2` `8` tăng / giảm âm lượng. Hết bài tự sang bài kế; tự tạm dừng khi có cuộc gọi. Thoát khỏi màn hình Nhạc thì nhạc dừng. Cần quyền truy cập nhạc.
- **Zalo Web:** Zalo bản web (chat.zalo.me) nhúng **trong ô LCD phía trên**, luôn nằm gọn trong ô. Giao diện máy tính, chụm hai ngón để phóng to/thu nhỏ. Đăng nhập chọn "Với số điện thoại" rồi xác nhận trong app Zalo trên chính máy này (mã QR cần máy khác để quét); lần sau không phải đăng nhập lại. Chỉ nhắn tin, không gọi; không có thông báo; chỉ vào được tên miền Zalo. Phím mềm trái "Full" / xoay ngang = toàn màn hình, như TV và YouTube. AI: "mở Zalo" hoặc "mở Zalo Web".
- **TV và YouTube (app con, tách từ repo TV):** nằm ngay trên Cài đặt, chạy **bên trong ô LCD phía trên** (WebView nhúng, không mở cửa sổ ngoài, không còn gọi app "Tube for me"/YouTube cài trên máy). Cần Internet.
  - **TV:** trang chọn đài (Vĩnh Long, Cà Mau, HTV9, Đồng Tháp…; danh sách ở `assets/channels.json`) -> chọn kênh -> xem, video phóng kín khung LCD. Về = lùi từng cấp (đang xem -> kênh -> đài -> Menu).
  - **YouTube:** chỉ vào được youtube.com, có chặn quảng cáo (script tự bấm "Bỏ qua"). Về = lùi từng trang, hết thì về Menu.
  - Phím ▲▼◀▶ và OK chuyển vào trang (cuộn / chọn); chạm trực tiếp vào LCD cũng được. Rời app con là dừng hẳn video/âm thanh.
  - **AI giọng nói:** "mở TV", "xem kênh HTV9", "mở đài Cà Mau", hoặc nói thẳng tên kênh ("HTV9") -> mở TV và phát đúng kênh; "mở YouTube", "mở YouTube tìm Sơn Tùng", "xem video Sơn Tùng", "tìm … trên YouTube" -> mở YouTube và tìm luôn. "Mở bài …" / "phát nhạc …" vẫn là nhạc trong máy. Thêm câu lệnh mới ở `AiParser.kt`; danh sách kênh nhận ra giọng nói ở `WebAi.kt`.
  - Mã: `WebPanes.kt` (YouTubePane, TvPane), `WebScripts.kt` (JS chặn quảng cáo / phóng video), `WebAi.kt` (nhận tên kênh).
- **Ghi âm:** nằm ngay dưới Lịch. OK để ghi/dừng và lưu, chọn bản ghi + OK để nghe lại, `#` để xóa
- **Máy tính:** + − × ÷ = , và ô phép tính lớn ở trên. Phím cứng: số, ▲ cộng, ▼ trừ, ◀ nhân, ▶ chia, OK bằng, `*` dấu phẩy, `#` xóa hết
- **Máy ảnh:** xem trước ngay trong ô LCD, ▼ đổi chụp ↔ quay, OK để chụp hoặc bắt đầu/dừng quay (ảnh vào Pictures/Nokia, video vào Movies/Nokia), ◀▶ đổi camera trước/sau
- **Thư viện:** xem ảnh trong máy, ◀▶ hoặc vuốt ngang để chuyển ảnh. Phím mềm trái **Xóa** xóa ảnh đang xem: Android 11 trở lên hệ thống tự hiện hộp thoại xác nhận, bản cũ hơn thì app hỏi "Xóa ảnh này?" (Có / Không). Ảnh đã xóa khỏi bộ nhớ máy thật, không ẩn
- **Khóa nhanh:** bấm phím Tắt (đỏ) 2 lần liên tiếp (lần 1 về màn hình chờ, lần 2 khóa màn hình của app, màn hình máy vẫn sáng). Mở khóa: Menu rồi *.
- Màn hình chờ: bấm số + Gọi để gọi; nút Home về màn hình chờ, Back lùi một cấp
- Chia đôi màn hình + điều khiển app khác bằng Trợ năng (bật trong Cài đặt máy > Trợ năng > phonecuibap)

Build APK: push lên GitHub -> Actions -> "Build APK" -> tải artifact `phonecuibap-apk`.
Đặt làm launcher: Cài đặt máy -> Ứng dụng mặc định -> Ứng dụng màn hình chính -> phonecuibap.

## Gọi / nhận cuộc gọi kiểu Nokia

Cuộc gọi đi và đến hiển thị ngay trên màn hình LCD (tên + số, đồng hồ đếm giờ, phím mềm), điều khiển bằng phím Gọi (nghe), phím Tắt (từ chối / cúp), phím mềm, D-pad và phím số (gửi DTMF).

- Bật: Menu -> Cài đặt -> **Ứng dụng gọi** -> chọn phonecuibap làm ứng dụng gọi điện mặc định (bắt buộc để nhận cuộc gọi đến, Android không cho app thường tự vẽ giao diện cuộc gọi).
- Cuộc gọi đến: Gọi/Nghe = trả lời, Tắt/Từ chối = từ chối, OK = tắt chuông. Hiện cả trên màn hình khóa.
- Đang gọi: Menu = Loa ngoài, Micro, Giữ cuộc gọi, Ghi âm cuộc gọi, Kết thúc. Máy 2 SIM sẽ hỏi chọn SIM.
- Nếu chưa đặt làm mặc định, cuộc gọi vẫn dùng giao diện của hệ thống như cũ.

## App con Ghi âm

Menu -> Ghi âm (hoặc biểu tượng "Ghi âm Nokia" riêng): ghi, tạm dừng/tiếp tục bằng phím ▲, thanh mức âm, nghe lại có thanh tiến trình, xóa có xác nhận (#).
Bản ghi lưu trong bộ nhớ riêng của app (`files/recordings`), dùng chung với tính năng ghi âm cuộc gọi.
Ghi âm cuộc gọi thu bằng micro nên cần bật loa ngoài mới có tiếng bên kia; hãy báo cho người đối diện khi ghi âm.

Xóa tin nhắn thật: Menu -> Cài đặt -> "Ứng dụng nhắn tin" -> chọn Nokia làm ứng dụng SMS mặc định.
Khi đó xóa tin (phím #) sẽ xóa hẳn khỏi hộp thư của máy, tin gửi/nhận được app tự lưu.
Chưa đặt làm mặc định thì "xóa" chỉ ẩn khỏi app. Lưu ý: chưa hỗ trợ MMS (tin có ảnh).
