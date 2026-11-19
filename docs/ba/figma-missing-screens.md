# Mô tả các màn hình còn thiếu trong Figma (bản gửi designer)

Ngày lập: 2026-11-19. Nguồn: PRD v0.3, SRS v1.2, đối chiếu với page "Design" và page "Design System" của file Figma `CYuJRLBrMAXMwlLe43uZyy` (so theo tên frame).
Tài liệu phái sinh để thiết kế; lệch PRD/SRS thì PRD/SRS đúng. Mục có ghi **[BQ-xx]** là câu hỏi chủ dự án chưa trả lời: thiết kế được khung, không chốt nội dung.
Dữ liệu trong mô tả (tên khóa, số liệu) chỉ là ví dụ minh họa.

## 0. Quy chuẩn chung (theo page "Design System" của Figma)

Nguồn: page **Design System** (`0:1`) của file Figma, gồm: frame "Design System" v2.0 "Sunrise Orange" (107:34), section "Components" (70:2), "Hướng A · Style board" (141:34), "Linh vật Cam" (108:34, 318:8014) và "Mindmap các trang" (46:2). Đã đối chiếu thêm với các màn thật ở page Design (Trang chủ, Link kích hoạt, Thanh toán VietQR).
Tinh thần: "Cam tươi, nền kem ấm, thẻ bo tròn có bóng mềm; vui và có động lực như trò chơi nhưng vẫn rõ ràng để học bài dài." Tỷ lệ màu **60% nền Cream, 30% thẻ trắng, 10% cam**.

**Khung và lưới**
- Mỗi màn có 2 frame: Desktop 1440 × 820 (cao hơn nếu cuộn) và Mobile 390 × 844. Đặt tên `Tên màn · Desktop` / `· Mobile`; màn có trạng thái: `Tên màn · Trạng thái · Desktop`.
- Nền trang Cream `#FFF8F1`; nội dung nằm trong tấm trắng bo lớn (32px) cách mép khoảng 12px (như Trang chủ).
- Khoảng cách theo thang 4px: 8, 16, 24, 32, 48 (biến: xs 4, sm 8, md 12, lg 16, xl 20, 2xl 24, 3xl 32, 4xl 40). Khoảng giữa các thẻ 16–24; lề trang desktop 44, mobile 16.
- Vùng chạm tối thiểu 48 × 48px (icon ô 48, icon bên trong ~2.2 lần chữ).
- Desktop học viên: thanh dọc bên trái, logo ngang trên cùng; các màn đã làm có 5 mục (Trang chủ, Khóa học, Bài giảng, Bài tập, Hồ sơ). Mobile: thanh tab dưới, mục chọn có nền Orange 50 bo tròn, icon và chữ Orange 700.
  - **Lệch trong Figma:** khối "Điều hướng di động" của Design System ghi **3 tab** (Học, Khóa học, Hồ sơ) + nút nổi Cam, Bài giảng và Bài tập nằm trong Khóa học; còn các màn đã thiết kế dùng **5 tab**. Tài liệu này theo 5 tab của màn thật; chủ dự án chốt lại.
- Trợ lý Cam là nút nổi tròn ở góc dưới phải (không phải tab), bấm mở khung chat.
- Desktop nhân sự: thanh icon dọc theo vai (mẫu Admin: Tổng quan, Gói bán, Link kích hoạt, CMS). Mobile nhân sự cùng cách.
- Header học viên: lời chào + câu phụ bên trái; bên phải chip XP (vàng), chip chuỗi ngày (ngọn lửa), nút chuông.

**Màu (token Figma, dùng đúng hex)**

| Vai trò | Tên trong Figma | Hex | Dùng cho |
|---|---|---|---|
| Thương hiệu | Sunrise Orange 500 | `#F76707` | icon, thanh tiến độ, streak, gradient; chữ trắng chỉ từ 24px đậm |
| Nút CTA | Orange 600 | `#E8590C` | nền nút chính; chữ trắng chỉ từ 18px đậm |
| Chữ cam, nút nhỏ | Orange 700 | `#C2410C` | chữ cam trên nền sáng, nút nhỏ chữ trắng; nhãn `#9A3412` |
| Nền chọn | Orange 50 | `#FFF1E6` (+ viền `#FFD8BF`) | tab đang học, chip, thẻ được chọn |
| XP, huy hiệu | Sun Gold | `#F59F00` (nền nhạt `#FFF4D6`, chữ `#854D0E`) | sao, cúp, XP |
| Nền trang | Cream | `#FFF8F1` | nền, 60% giao diện |
| Thẻ | Card White | `#FFFFFF` + viền `#F3E3D5` | thẻ, 30% giao diện |
| Chữ | Ink | `#1F2433` chính, `#5B6170` phụ (tương phản 5.9:1), `#6B7280` mờ | |
| Thành công | Success | `#16A34A`, nền `#E7F8EE`, chữ `#166534` | đáp án đúng |
| Lỗi | Error | `#DC2626`, nền `#FEE2E2` | lỗi ngữ pháp, sai; khác cam thương hiệu |
| **AI gợi ý** | AI Suggestion | **`#31566B` (xanh slate), nền `#EAF1F4`** | trợ giúp, gợi ý của Cam, nút "Hỏi trợ lý AI" |

- **Không dùng màu tím cho AI.** Tím chỉ còn là chip mẫu của loại từ "Từ kết nối" (tag từ loại là màu mẫu, danh sách chính thức chờ Phòng Đào tạo).
- **Gradient hero**: `#E8590C → #F76707 → #FF8A3D`, góc 120°. Chỉ một khối nổi bật mỗi màn (hero, thẻ hoàn thành bài). Chữ nhỏ trên gradient phải đặt trong nhãn trắng.

**Chữ (Nunito, có tiếng Việt)**

| Cấp | Cỡ/dòng | Độ đậm | Ví dụ |
|---|---|---|---|
| Display | 30/38 | 900 | "Hôm nay bạn học gì?" |
| Heading | 28/36 | 900 | tiêu đề màn |
| Section | 20/28 | 900 | "Topic 3 · Consumerism" |
| Card title | 17–18/26 | 800 | tên thẻ |
| Body | 16/26 | 500–600 | nội dung bài, câu hỏi (không nhỏ hơn 16px) |
| Số liệu | 22–36 | 900 | "92/100", "1.240 XP" |
| Label | 12 | 800 | nhãn chip, tiêu đề cột |

**Hình khối, đổ bóng**
- Bo góc: Hero 28, Thẻ 24, Ô icon 16, Nút/tag dạng **viên thuốc** (pill, 999). Biến Figma: 12 / 16 / 24 / 32 / full.
- Bóng **ấm ánh cam, không dùng bóng xám**. Thẻ: viền mảnh + bóng nhẹ; nút CTA và hero có bóng cam mềm. (Các màn đã vẽ và `Web/tailwind.config.js` còn dùng "cạnh dày" 2–4px dưới nút/thẻ; chủ dự án chọn một kiểu thống nhất, mặc định theo màn đã vẽ.)

**Thành phần (có trong section Components)**
- **Nút** (đều ≥ 48px, pill): Chính (gradient/Orange 600, chữ trắng, kèm mũi tên →), Phụ (nền trắng, viền cam, chữ Orange 700), Nhỏ (Orange 700 nền, chữ trắng), Chữ ("Để sau"), Pressed (đậm hơn), Disabled (xám), **AI** (nền `#31566B`, chữ trắng, bóng mềm), Nút mạng xã hội Google/Apple (trắng, viền).
- **Ô nhập**: nhãn trên, icon trái, bo 16, viền `#F3D9C4`, nền `#FFFCF9`; 3 trạng thái: mặc định, focus (viền cam), lỗi (viền đỏ + thông báo). Checkbox cam.
- **Chip/huy hiệu**: chuỗi ngày (ngọn lửa), XP (vàng), cấp ("★ Lv.5 · Tự tin" nền Ink chữ vàng), trạng thái (Đang học cam, Hoàn thành xanh, Trả phí vàng + khóa, AI xanh slate, Xám trung tính).
- **Thanh tiến độ** cam; **ngày chuỗi**: đã học (tròn cam, dấu tick), hôm nay (tròn nét đứt), chưa tới (xám).
- **Thẻ số liệu**: ô icon 48 nền nhạt + số 22px 900 (vd. "3/5 Bài trong Topic 2").
- **Thẻ việc** (Task card): đang học (viền cam), tiếp theo (trắng), AI (xanh slate), thưởng +XP (vàng).
- **Lộ trình Topic**, 3 trạng thái: hoàn thành (nền xanh nhạt, tick, "+60 XP"), đang học (viền cam, nền cam nhạt, số trong ô cam, chip "Đang học"), khóa (nền xám `muted`, icon khóa, chip "Trả phí").
- **Đáp án trắc nghiệm**, 4 trạng thái: mặc định ("Mặc định"), đã chọn (viền cam + tick "Đã chọn"), đúng (xanh + "✓ Đúng"), sai (đỏ + "✕ Sai"); dưới có khối giải thích. **Đúng/sai luôn có icon và chữ, không chỉ dựa vào màu.**
- **Kết quả chấm AI**: số lớn "78/100" (Orange 700) + chip điểm mạnh/lỗi. **Phản hồi trước/sau**: tab "Bài của bạn" / "Bản AI sửa", chú giải: lỗi ngữ pháp (đỏ), cần từ nối tốt hơn (cam), đã sửa (xanh), AI gợi ý (xanh slate).
- **Hội thoại dẫn dắt** (Viết phát triển ý): bong bóng Cam/AI nền `#EAF1F4` bên trái, bong bóng học viên Orange 600 chữ trắng bên phải.
- **Quick access card**, **Speech bubble** của Cam, **Nav item**, **Tab bar item**, **Stat card**.

**Linh vật, logo, chuyển động**
- **Cam** (mèo cam, 7 tư thế, bộ ảnh ở section "Cam · Hành động") xuất hiện ở hero, màn hoàn thành bài, thông báo và **trạng thái trống**; sao lấp lánh làm điểm nhấn, tối đa 2–3 mỗi khối. Mọi màn rỗng dùng Cam thay vì chữ trơn.
- **Logo**: ngang cho cỡ nhỏ (sidebar: biểu tượng cao 44px, chữ 16px; header mobile: 36px/14px), dọc cho cỡ lớn (đăng nhập desktop cao 64px), favicon chỉ biểu tượng. Trên nền cam luôn đặt trong tấm trắng bo góc.
- **Chuyển động**: rung nhẹ khi chọn sai, confetti khi hoàn thành bài và giữ chuỗi; chờ AI 3–5s luôn có animation tiến độ; tôn trọng `prefers-reduced-motion`.
- **Truy cập** (WCAG 2.1 AA): chữ trắng trên Orange 600 chỉ từ 18px đậm; chữ cam trên nền sáng dùng Orange 700; thông tin không chỉ dựa vào màu.

**Mindmap các trang (Figma 46:2)** là bản sitemap cũ theo SRS v1 (còn "combo", "Gói Token Add-on"), chỉ dùng để xác nhận các màn chủ dự án đã dự kiến: Chi tiết khóa học, Bài giảng mẫu và bài tập thử, Bài test đầu vào, Cài đặt thông báo, Trung tâm thông báo, Hết lượt AI → Nạp thêm, Giỏ hàng, Lịch sử đơn hàng. PRD/SRS mới là chuẩn, không dùng combo.

**Quy tắc cho mọi màn**
- Có đủ trạng thái: bình thường, đang tải (skeleton), rỗng (có Cam), lỗi (có nút thử lại), mất mạng khi cần.
- Thông báo lỗi nằm tại ô hoặc đầu khối, tiếng Việt, nói cách khắc phục. Không hiển thị "token", chỉ "lượt".
- Thao tác không hoàn tác được (rút khỏi lớp, thu hồi, nộp bài) luôn có hộp xác nhận, theo mẫu "Xác nhận thu hồi link".
- Màn nhân sự có thao tác sửa dữ liệu: bắt buộc ô "Lý do" khi FR yêu cầu, hiển thị dòng "Sẽ ghi nhật ký".
- Màn giao dịch theo mẫu "Thanh toán VietQR": thẻ trạng thái ("Đang chờ thanh toán" vàng), khối số tiền và nội dung chuyển khoản.

## 1. Thứ tự ưu tiên đề xuất

| Nhóm | Phạm vi | Lý do |
|---|---|---|
| P1 | Học viên khóa tự học: A1–A9, B1 | Web giai đoạn đầu là khóa tự học, đã có code backend và web cho nhiều phần |
| P2 | Khóa có lớp: C (học viên), D (giáo viên/trợ giảng), E (giáo vụ), F (kế toán) | Web giai đoạn sau, sản phẩm chính theo D-117 |
| P3 | Hỗ trợ và quản trị: G, H | Cần cho vận hành, ít phụ thuộc thiết kế học viên |

---

## A. Học viên, khóa tự học (P1)

### A1. Kiểm tra đầu vào (FR-TEST-01, FR-TEST-02, FR-GOAL-03)
Tác nhân: học viên (khách chưa đăng nhập: **[BQ-06]**).
Số frame: 5 màn × 2.

1. **Giới thiệu bài kiểm tra**
   - Tiêu đề "Kiểm tra đầu vào", chip "Miễn phí", chọn chương trình (nếu có nhiều).
   - Thẻ thông tin: số phần, tổng thời gian, "Không thể tạm dừng", "Hết giờ hệ thống tự nộp".
   - Danh sách kỹ năng gồm trong đề (icon + tên). Nút chính "Bắt đầu làm bài", nút phụ "Tự chọn trình độ".
   - Nếu đã làm: hiện kết quả gần nhất; số lần làm lại **[BQ-06]** (để ô giữ chỗ).
2. **Làm bài** — dùng lại giao diện Mô phỏng thi IELTS (đề trái, câu hỏi phải, thanh điều hướng câu, đánh dấu xem lại, đồng hồ đếm ngược). Không có Cam gợi ý, không nút tạm dừng.
3. **Xem lại và nộp** — dùng lại frame 00.02/00.04 của IELTS, đổi tiêu đề.
4. **Trạng thái đặc biệt**: banner vàng "Mất kết nối, câu trả lời đã lưu trên máy, sẽ gửi khi có mạng"; hộp "Hết giờ, bài đã được nộp".
5. **Kết quả**
   - Đầu trang: thẻ lớn "Level của bạn" (vd. Level 2) + band tổng, mascot Cam chúc mừng.
   - Lưới band từng kỹ năng (thanh ngang hoặc 4 thẻ nhỏ). Phần chấm AI chưa xong: thẻ kỹ năng hiện "Đang chấm" (chip AI xanh slate `#EAF1F4`) và dòng "Level tạm tính".
   - Khối "Khóa gợi ý cho bạn": 1–3 thẻ khóa (tên, mức đầu vào–đầu ra, thời hạn, giá, nút "Xem khóa"), không có combo. Nếu mục tiêu cao hơn đầu ra khóa: thêm dòng "Sau khóa này, bạn nên học tiếp: …" theo thứ tự.
   - Nút "Lưu vào hồ sơ" không cần (tự lưu), chỉ cần "Xem khóa gợi ý" và "Làm lại" (nếu được).

### A2. Chi tiết khóa và học thử (FR-LMS-01, FR-AUTH-02, FR-COM-01)
Tác nhân: khách, học viên chưa mua, học viên đã mua.
Hiện có "Khóa học · Tất cả khóa" nhưng chưa có trang chi tiết.
- Đầu trang: tên khóa, chương trình, chip **loại khóa**: "Tự học" (xanh nhạt) hoặc "Có lớp" (cam, kèm icon nhóm). Ảnh bìa, mức đầu vào → đầu ra.
- Tab hoặc cuộn theo khối: Mô tả, Lộ trình (cây Level → Topic, Topic đầu có chip "Học thử", các Topic khác khóa), Bài kiểm tra (chương, cuối khóa), Ai nên học.
- Khối giá bên phải (desktop) / thanh dính đáy (mobile): chọn thời hạn 6 hoặc 12 tháng (2 thẻ chọn), giá, lượt AI mỗi tuần, nút "Mua khóa" và "Học thử".
- Khóa có lớp: thêm khối "Cách học": học ngay phần tự học sau khi mua, giáo vụ xếp lớp sau, lịch lớp thông báo qua email/thông báo. Không hứa ngày khai giảng cụ thể.
- Trạng thái theo người xem: chưa đăng nhập (nút dẫn tới đăng nhập), đã mua còn hạn (nút "Vào học", hiển thị ngày hết hạn, nút "Gia hạn"), đã hết hạn.

### A3. Học tuần tự: trạng thái khóa unit (FR-LMS-04)
Không phải màn mới, là **bổ sung vào** Bài giảng · Danh sách bài, Bài tập · Danh sách bài tập và Lộ trình trên Trang chủ.
- Unit bị khóa: nền `muted`, icon khóa, dòng "Hoàn thành Unit trước để mở".
- Bấm unit khóa mở **bảng điều kiện**: ba dòng có tick — "Xem bài giảng đạt X%", "Làm xong bài tập", "Nộp kiểm tra chương" (X% do admin đặt, hiển thị tiến độ hiện tại, vd. 62/80%). Nút "Học phần còn thiếu".
- Unit được giáo viên mở sớm: chip "Được mở sớm" (vàng).
- Khóa tắt học tuần tự: không hiện khóa nào.

### A4. Bài kiểm tra của khóa (FR-EXAM-02, FR-EXAM-03)
Tác nhân: học viên đã mua còn hạn; học thử không được thi.
1. **Danh sách bài kiểm tra** trong khóa: thẻ từng bài (Kiểm tra chương 1…, Kiểm tra cuối khóa), trạng thái (chưa làm, đã làm + điểm, bị khóa), số lần làm lại còn lại, thời gian làm bài, nút "Làm bài". Số lần thi và điều kiện mở bài cuối khóa **[BQ-07]**.
2. **Làm bài**: dùng giao diện mô phỏng thi (không tạm dừng, không Cam).
3. **Kết quả**: điểm theo thang chương trình (IELTS: band từng kỹ năng + band tổng), số câu đúng, khối "Xem lại đáp án" từng câu có giải thích. Writing/Speaking đang chấm: chip "Đang chấm", báo trừ 1 lượt từ ví của khóa. Chứng nhận **[BQ-07]** (chỉ để ô giữ chỗ).
4. Với kiểm tra chương: dòng "Đã nộp, Unit tiếp theo đã mở" (hoặc "Còn thiếu: …").

### A5. Ví lượt AI và mua thêm lượt (FR-AI-04, FR-COM-05)
- **Thẻ ví lượt** (đặt ở Hồ sơ, đầu màn Trợ lý AI và Trang chủ khối "Lượt chấm AI"): thanh tiến độ "Còn X/Y lượt tuần này", dòng "Nạp lại 00:00 thứ Hai", dòng phụ "Lượt mua thêm: N". Không nhắc token.
- **Hết lượt**: hộp mời "Bạn đã dùng hết lượt. Mua thêm lượt hoặc chờ thứ Hai". Hai nút: "Mua thêm lượt", "Để sau".
- **Chọn gói lượt**: lưới thẻ 10, 20, 50, 100, 200, 500 lượt (mỗi thẻ: số lượt lớn, giá; nhãn "Phổ biến" tùy chọn). Dòng giải thích "Lượt mua thêm không reset theo tuần". Hạn dùng pool và giá **[BQ-03]**. Nút "Tiếp tục thanh toán" dẫn vào luồng "Xác nhận đơn hàng" đã có.
- **Lịch sử tiêu thụ** (tùy chọn, P3): danh sách ngày, tác vụ, số lượt trừ.

### A6. Gia hạn khóa (FR-COM-06)
- Điểm vào: thẻ khóa trong Hồ sơ/Đơn hàng và trang chi tiết khóa khi còn hạn; banner nhắc khi còn ≤ 30 ngày (con số do dự án chốt).
- Màn "Gia hạn": hiện khóa, ngày hết hạn hiện tại, chọn 6/12 tháng, dòng "Ngày hết hạn mới" tính trước. Cách tính (cộng dồn hay tính lại) **[BQ-02]**: thiết kế sẵn ô, chờ quyết định.
- Sau đó dùng lại Xác nhận đơn hàng → VietQR → Thanh toán thành công.

### A7. Nhận khóa bằng link kích hoạt (FR-COM-04)
Tác nhân: người dùng mở link do admin/tư vấn viên gửi.
- **Trang đích**: tên sản phẩm, thời hạn, lượt AI đi kèm, nút "Kích hoạt". Chưa đăng nhập: nút đổi thành "Đăng nhập để kích hoạt" rồi quay lại trang này.
- **Chưa xác thực email**: thẻ vàng "Hãy xác thực email trước" + nút gửi lại email.
- **Kết quả**: thành công (xanh, "Khóa đã mở, hết hạn ngày …", nút "Vào học"); lỗi (đã dùng link này, link đã thu hồi, link hết hạn, hết lượt dùng): mỗi lỗi một thông báo riêng, màu đỏ nhạt, có nút về Trang chủ.

### A8. Thông báo (FR-RET-03, FR-CLS-03, FR-SES-03, FR-ASG-01..03)
- **Chuông trong header** có chấm đỏ số lượng. Bấm mở panel (desktop: popover phải; mobile: trang đầy đủ).
- **Danh sách thông báo**: dòng gồm icon theo loại, tiêu đề, một dòng mô tả, thời gian tương đối, chấm xanh chưa đọc. Loại: kết quả AI chấm xong, nhắc chuỗi ngày, nhắc bài được giao/sắp đến hạn, xếp hoặc chuyển lớp, buổi học đổi/hủy, điểm sửa/nhận xét giáo viên, nhân viên trả lời ticket, đơn hàng. Nút "Đánh dấu đã đọc hết". Bấm dòng mở đúng trang liên quan.
- **Trạng thái rỗng**: mascot Cam + "Chưa có thông báo".
- **Cài đặt thông báo** (trong Hồ sơ): công tắc bật/tắt Web Push, chọn các loại muốn nhận. Trạng thái trình duyệt chặn thông báo: thẻ vàng hướng dẫn mở lại.

### A9. Đăng ký có ngày sinh và chấp thuận điều khoản (FR-AUTH-01, D-114)
Bổ sung vào màn Đăng ký đã có.
- Thêm ô **Ngày sinh** bắt buộc (3 ô chọn ngày/tháng/năm hoặc date picker, nhãn rõ). Lỗi tại ô: "Ngày sinh không hợp lệ", "Ngày sinh không thể ở tương lai".
- Chọn xong ngày sinh: dưới 18 tuổi hiện khối "Dành cho người dưới 18 tuổi" yêu cầu phụ huynh chấp thuận bộ điều khoản riêng (checkbox + link đọc điều khoản); từ 18 tuổi: checkbox điều khoản thường. Cách chấp thuận cụ thể (xác nhận qua email phụ huynh hay tick) **[BQ-17]**.
- Đăng ký Google: bước phụ "Bổ sung thông tin" (ngày sinh + điều khoản) trước khi vào học.

### A10. Việc cần xác nhận (không chắc có trong Figma)
Chưa mở frame để kiểm tra: popup giải nghĩa từ và chấm phát âm từng từ (FR-AI-05: nút mic, điểm cả từ, từng âm, âm đọc nhầm), Viết phát triển ý (FR-AI-01: hỏi đáp dẫn dắt rồi ghép nháp), XP/danh hiệu (FR-RET-02: Tập sự, Tự tin, Chuyên gia). Nếu thiếu thì bổ sung theo mô tả này.

---

## B. Hỗ trợ học viên (P3, phía học viên)

### B1. Chatbox hỗ trợ (FR-SUP-01, FR-SUP-02)
Dùng lại khung chatbox nổi của Cam (Trợ lý AI · Đang mở), thêm chế độ hỗ trợ.
- **Cam trả lời FAQ**: bong bóng chat, gợi ý nhanh dạng chip ("Khóa của tôi còn hạn đến khi nào?", "Đơn gần nhất", "Lượt AI còn lại"). Câu về tiền, lỗi thanh toán hoặc Cam không chắc: Cam đề nghị "Gặp nhân viên" (nút nổi bật). Cam không hứa hoàn tiền, cộng lượt, đổi khóa.
- **Chat với nhân viên**: sau khi bấm "Gặp nhân viên" hiện thẻ trạng thái "Đã tạo ticket · Chờ nhân viên" → "Nhân viên X đang hỗ trợ". Lịch sử chat với Cam giữ phía trên, ngăn bằng dòng "Bắt đầu trò chuyện với nhân viên".
- **Ngoài giờ làm việc**: thẻ vàng "Nhân viên phản hồi dự kiến …" + nút Zalo, gọi hotline. Giờ làm việc, cam kết phản hồi **[BQ-09]**.
- **Danh sách ticket của tôi** (trong Hồ sơ): mã, tiêu đề, trạng thái (Đang chờ, Đang xử lý, Đã đóng), thời gian.
- Khách chưa đăng nhập: quyền hạn chế **[BQ-09]**.

---

## C. Học viên, khóa có lớp (P2)
Chỉ hiện khi học viên có khóa loại "Có lớp".

### C1. Trang "Lớp của tôi"
- **Trạng thái chờ xếp lớp**: thẻ vàng "Bạn đang chờ xếp lớp. Trong lúc chờ, hãy học phần tự học" + nút "Học phần tự học". Hiện ngày mua.
- **Đã có lớp**: tên lớp, giáo viên chính, trợ giảng (avatar + tên), hình thức (Online/Offline), ngày bắt đầu–kết thúc, nút xem lịch.
- **Lịch buổi học**: danh sách theo ngày; mỗi buổi: giờ, người dạy, loại (nhóm / kèm 1-1), Online có nút "Vào lớp" mở link Meet/Zoom, Offline chỉ có giờ (có địa điểm nếu dự án cung cấp). Buổi bị hủy: gạch ngang + chip đỏ. Buổi đã đổi giờ: chip "Đã đổi".
- Chuyên cần của tôi: thống kê nhỏ (có mặt, vắng, muộn, vắng có phép), chỉ xem, **không có nút tự điểm danh**.

### C2. Bài được giao (FR-ASG-02, FR-ASG-03)
- **Danh sách bài được giao**: tab Cần làm / Đã nộp. Thẻ bài: tên, loại (bài tập, luyện tập, viết, nói), hạn nộp, trạng thái (chưa làm, sắp hết hạn đỏ nhạt, đã nộp, nộp trễ cam).
- **Làm bài**: dùng lại giao diện bài tập/luyện tập/Viết/Nói đã có, thêm thanh đầu "Bài giáo viên giao · Hạn …". Dòng "Bài này không trừ lượt AI tuần của bạn" (hoặc giới hạn riêng **[BQ-19]**).
- **Kết quả**: điểm AI hiện ngay; khối "Nhận xét của giáo viên" ban đầu rỗng ("Chờ giáo viên nhận xét"); khi giáo viên sửa điểm: hiện điểm mới, điểm AI gạch nhẹ, lời nhận xét kèm tên và thời gian.
- Nộp trễ vẫn nhận bài, gắn chip "Nộp trễ".

---

## D. Giáo viên và trợ giảng (P2, web desktop là chính, mobile xem nhanh)

Thanh điều hướng: Lớp của tôi, Lịch dạy, Bài được giao, Chấm và nhận xét.
Quyền chỉ trong lớp được phân công (NFR-SEC-05): màn không bao giờ hiện lớp khác.

### D1. Danh sách lớp (FR-CLS-05)
Thẻ lớp: tên, khóa, hình thức, sĩ số hiện/tối đa, tiến độ trung bình, số học viên "tụt lại" (chip đỏ nhạt), buổi kế tiếp.

### D2. Chi tiết lớp: bảng tiến độ (FR-CLS-05)
- Đầu trang: tên lớp, ngày, nút "Lịch buổi học", "Giao bài", "Mở sớm unit".
- Bảng học viên: tên, % bài giảng, unit hiện tại, bài giao (đã nộp / chưa / trễ), điểm trung bình, điểm kiểm tra chương, chuyên cần %.
- Dòng học viên **tụt lại**: nền đỏ nhạt + chip lý do (quá hạn chưa nộp, vắng nhiều, chậm hơn lớp, điểm thấp). Bộ lọc "Chỉ hiện tụt lại", tìm theo tên.
- Mobile: bảng thành danh sách thẻ, bấm mở chi tiết.

### D3. Lịch buổi học (FR-SES-01..03)
- Lịch tuần/tháng + danh sách. Nút "Tạo buổi".
- **Tạo/sửa buổi**: ngày, giờ bắt đầu–kết thúc, người dạy, hình thức. Online: ô link bắt buộc. Tùy chọn "Lặp hằng tuần đến hết ngày lớp". Lỗi: buổi ngoài ngày lớp, giờ kết thúc trước bắt đầu, thiếu link.
- **Đổi/hủy buổi**: hộp xác nhận, cảnh báo "Học viên sẽ nhận thông báo". Buổi đã điểm danh: nút đổi bị khóa kèm lý do.
- Buổi kèm 1-1 do giáo vụ xếp: hiện chip "Kèm 1-1" và tên học viên.

### D4. Điểm danh (FR-SES-04)
- Danh sách học viên của buổi; mỗi dòng có nhóm 4 lựa chọn: Có mặt, Vắng, Đi muộn, Vắng có phép (nút đoạn, mỗi trạng thái một màu). Nút "Đánh dấu tất cả có mặt". Ô ghi chú tùy chọn.
- Buổi chưa diễn ra: khóa, "Chưa đến giờ buổi học".
- **Sửa điểm danh**: được sửa mọi lúc; mỗi lần sửa hiện "Đã sửa bởi … lúc …, giá trị cũ: …". Xem lịch sử sửa qua icon đồng hồ.

### D5. Giao bài (FR-ASG-01)
1. Bước 1: chọn bài (duyệt cây khóa → Topic → bài, chỉ bài đã xuất bản; có tìm kiếm). Loại: bài tập sẵn, luyện tập, viết/nói AI chấm. **Không có soạn bài, không có tải file.**
2. Bước 2: chọn người nhận (cả lớp hoặc chọn học viên), đặt hạn nộp (ngày + giờ).
3. Bước 3: xác nhận tóm tắt. Lỗi: bài chưa xuất bản, hạn ở quá khứ, giao trùng (cùng bài, cùng lớp, cùng hạn), học viên không thuộc lớp.
4. Ghi chú: bài ở unit chưa mở sẽ chỉ mở riêng bài đó cho người được giao.

### D6. Theo dõi bài được giao và chấm (FR-ASG-03, FR-ASG-04)
- Danh sách bài đã giao: tên, hạn, tiến độ nộp (vd. 18/25), nút xem.
- Chi tiết bài: bảng học viên (đã nộp, chưa nộp, nộp trễ, điểm AI, điểm cuối).
- **Màn chấm một bài nộp**: bên trái bài làm (với bài viết: hiển thị đánh dấu lỗi của AI), bên phải điểm AI (chỉ đọc), ô nhập điểm mới, ô nhận xét, nút "Lưu". Mỗi lần lưu tạo bản mới; nút "Lịch sử chỉnh" liệt kê các bản. Điểm cuối là bản mới nhất.

### D7. Mở sớm unit (FR-CLS-06)
Hộp thoại từ chi tiết lớp: chọn unit, chọn "Cả lớp" hoặc từng học viên, nút "Mở". Hiển thị ai đã mở, lúc nào. Khóa tắt học tuần tự: nút ẩn.

---

## E. Giáo vụ / Quản lý đào tạo (P2)

Thanh điều hướng: Danh sách chờ xếp lớp, Lớp học, Lịch, Giáo viên, Báo cáo.

### E1. Danh sách chờ xếp lớp (FR-CLS-02)
Bảng học viên đã mua khóa có lớp chưa có lớp: tên, khóa, ngày mua, hạn khóa, nút "Xếp lớp". Lọc theo khóa, sắp theo ngày mua.

### E2. Tạo và sửa lớp (FR-CLS-01)
Biểu mẫu: khóa (chỉ khóa có lớp), tên lớp, ngày bắt đầu, ngày kết thúc, hình thức (Online/Offline), sĩ số tối đa. Lỗi tại ô: ngày kết thúc trước bắt đầu, sĩ số ≤ 0, khóa không phải khóa có lớp. Lớp đã có học viên: không xóa, chỉ có nút "Đóng lớp" (hộp xác nhận).

### E3. Xếp, chuyển, rút học viên (FR-CLS-03)
- Hộp chọn lớp (chỉ lớp cùng khóa; lớp đầy/đã đóng bị mờ kèm lý do).
- Chuyển lớp: hiện lớp cũ → lớp mới. Rút khỏi lớp: hộp xác nhận.
- Tab "Lịch sử xếp/chuyển" ở chi tiết học viên.
- Lỗi: học viên chưa mua hoặc khóa hết hạn: từ chối kèm thông báo.

### E4. Phân công giáo viên, trợ giảng (FR-CLS-04)
Trong chi tiết lớp: một giáo viên chính (bắt buộc) + nhiều trợ giảng, mỗi người có ngày bắt đầu/kết thúc phụ trách. Đổi người: hộp "Kết thúc phân công cũ, tạo phân công mới", cảnh báo "Người cũ mất quyền với lớp ngay".

### E5. Buổi kèm 1-1 (FR-SES-02)
Chọn học viên + trợ giảng của lớp + giờ + link/giờ học. Dùng lại biểu mẫu buổi học.

### E6. Ngưỡng "tụt lại" (FR-CLS-05)
Trong cấu hình khóa: các ngưỡng quá hạn, số buổi vắng, độ chậm so với lớp, điểm thấp. Ô số có giá trị mặc định và mô tả ngắn.

---

## F. Kế toán (P2)

### F1. Báo cáo số buổi dạy (FR-CLS-07)
- Bộ lọc: khoảng ngày, vai (giáo viên/trợ giảng), lớp.
- Bảng: người dạy, số buổi nhóm, số buổi kèm 1-1, tổng giờ. Chỉ đếm buổi đã điểm danh. Nút "Xuất file".
- **Cảnh báo đối chiếu**: dòng vàng "Buổi bị sửa điểm danh sau lần xuất trước" có danh sách buổi, giá trị cũ → mới.
- Ghi chú trên màn: "Hệ thống không tính lương".
- Kế toán còn xem Báo cáo doanh thu và AI đã có; báo cáo riêng cho Kế toán **[BQ-12]**.

---

## G. Nhân viên hỗ trợ (P3)

Thanh điều hướng: Hộp thư ticket, Tra cứu học viên.

### G1. Hộp thư ticket (FR-SUP-02)
Danh sách ticket (mã, học viên, trạng thái, thời gian chờ), bộ lọc, nút "Nhận xử lý". Chi tiết: khung chat thời gian thực, bên phải thẻ thông tin học viên (khóa, hạn, đơn gần nhất, lượt còn lại), lịch sử chat với Cam ở phía trên cùng. Trạng thái và phân công **[BQ-09]**.

### G2. Tra cứu học viên (FR-SUP-03)
- Ô tìm theo email, tên hoặc mã đơn.
- **Hồ sơ học viên (chỉ xem)**: khóa và hạn, đơn, lượt AI, ticket. **Không có doanh thu tổng.**
- **Hành động có lý do**: kích hoạt khóa thủ công, cộng lượt bù, mở khóa tài khoản, gửi lại email xác thực. Mỗi hành động mở hộp: nhập lý do (bắt buộc), dòng "Sẽ ghi nhật ký", nút xác nhận.

---

## H. Admin và biên tập viên, phần còn thiếu (P3)

### H1. Phân quyền (FR-ADM-04)
- **Danh sách role**: tên, số quyền, số người, nút tạo role.
- **Chi tiết role**: quyền nhóm theo module (`module.action`), mỗi quyền có công tắc và **một câu mô tả tiếng Việt**. Khoảng 30–40 quyền.
- **Chi tiết nhân sự**: các role; bảng GRANT/REVOKE riêng từng quyền, ngày hết hạn tùy chọn; hiển thị "Quyền hiệu lực" cuối cùng (hợp role − REVOKE + GRANT). Không cho xóa quyền quản trị cuối cùng (hộp cảnh báo).
- Ma trận role mặc định: `docs/ba/use-cases.md` mục 7.1.

### H2. Nhật ký thao tác (FR-ADM-05)
Bảng chỉ đọc: thời điểm, ai, thao tác, đối tượng, giá trị trước/sau (so sánh 2 cột), lý do. Lọc theo người, đối tượng, ngày. Không có nút sửa/xóa.

### H3. Quản lý đề thi, đề đầu vào, bảng quy đổi (FR-ADM-06)
Biên tập viên: danh sách đề (loại đầu vào/thi khóa/luyện tập, trạng thái nháp/đã xuất bản/ngừng); trình soạn đề: chọn kỹ năng, phần, dạng câu, nhập câu hỏi, audio, bài đọc, hình, thời gian từng phần, chế độ tạm dừng; bảng quy đổi band (số câu đúng → band); xem trước; xuất bản. Đề đã có người làm: sửa đáp án phải cảnh báo "Sẽ chấm lại". Không xóa đề, chỉ "Ngừng xuất bản". Có thể tái dùng khung các màn Chỉnh sửa câu hỏi và Nhập câu hỏi đã có.

### H4. Cấu hình khóa: học tuần tự và kiểm tra (FR-LMS-04, FR-EXAM-02)
Trong chi tiết khóa của CMS: công tắc "Học tuần tự", ô "% xem bài giảng để mở unit sau", danh sách bài kiểm tra chương (số lần làm lại, thời gian), loại khóa (tự học/có lớp). Thêm: mốc nhắc lịch buổi học (FR-SES-03).

### H5. Quản lý người dùng
Danh sách học viên và nhân sự: tìm, lọc theo role, khóa/mở khóa tài khoản. Phần nhân sự cho Admin; phần học viên một phần dùng chung với G2.

---

## 2. Danh sách đối chiếu nhanh

| Mã | Màn | Nhóm | FR |
|---|---|---|---|
| A1 | Kiểm tra đầu vào (5 màn) | P1 | FR-TEST-01/02, FR-GOAL-03 |
| A2 | Chi tiết khóa, học thử | P1 | FR-LMS-01, FR-COM-01 |
| A3 | Trạng thái khóa unit | P1 | FR-LMS-04 |
| A4 | Bài kiểm tra khóa (4 màn) | P1 | FR-EXAM-02/03 |
| A5 | Ví lượt AI, mua thêm lượt | P1 | FR-AI-04, FR-COM-05 |
| A6 | Gia hạn khóa | P1 | FR-COM-06 |
| A7 | Nhận khóa qua link | P1 | FR-COM-04 |
| A8 | Thông báo (3 màn) | P1 | FR-RET-03 |
| A9 | Đăng ký ngày sinh, điều khoản | P1 | FR-AUTH-01 |
| B1 | Chatbox hỗ trợ, ticket | P3 | FR-SUP-01/02 |
| C1–C2 | Lớp của tôi, bài được giao | P2 | FR-CLS, FR-SES, FR-ASG |
| D1–D7 | Giáo viên, trợ giảng | P2 | FR-CLS-05/06, FR-SES, FR-ASG |
| E1–E6 | Giáo vụ | P2 | FR-CLS-01..05, FR-SES-02 |
| F1 | Báo cáo số buổi dạy | P2 | FR-CLS-07 |
| G1–G2 | Nhân viên hỗ trợ | P3 | FR-SUP-02/03 |
| H1–H5 | Quản trị còn thiếu | P3 | FR-ADM-04..06, FR-LMS-04 |

## 3. Câu hỏi mở chặn thiết kế chi tiết

BQ-02 (cách tính gia hạn), BQ-03 (giá và hạn lượt mua thêm), BQ-06 (cấu trúc đề đầu vào, làm lại, khách), BQ-07 (số lần thi, chứng nhận), BQ-09 (hỗ trợ: giờ, trạng thái, khách), BQ-12 (báo cáo Kế toán), BQ-17 (cách phụ huynh chấp thuận), BQ-19 (lượt AI cho bài giáo viên giao). Danh sách đầy đủ và trạng thái mới nhất: `docs/ba/decisions.md`.
