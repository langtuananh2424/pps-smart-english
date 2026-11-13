# BRD — PPS Smart English (bổ sung PRD v0.1)

Phiên bản 0.1 · 2026-11-13 · Trạng thái: **Nháp chờ chủ dự án duyệt**

Tài liệu yêu cầu kinh doanh, bổ sung cho `docs/PRD.md`. Không thay thế PRD/SRS. Quyết định và câu hỏi mở chi tiết ở [decisions.md](decisions.md).

## 1. Bối cảnh

PPS đang vận hành `pps-education` (LMS cho trường liên kết) và lớp IELTS offline. PPS Smart English đưa phương pháp "24 Nguyên tắc vàng" của thầy Nguyễn Hữu Phúc tới người tự học qua web và app, bán theo khóa, có AI chấm chữa.

Vấn đề người học gặp (PRD 3):
- Kẹt band IELTS vì hành văn rời rạc, thiếu từ nối, bí ý khi viết.
- Không quen thao tác thi trên máy tính.
- Không có người chữa bài thường xuyên.

## 2. Mục tiêu kinh doanh

| Mã | Mục tiêu | Chỉ số | Mức năm đầu (đề xuất, chờ duyệt) |
|---|---|---|---|
| BO-01 | Có doanh thu online từ 6 khóa IELTS | Doanh thu | Kịch bản cơ sở ~2 tỷ (mục 8) |
| BO-02 | Chuyển người dùng miễn phí sang trả phí | Tỷ lệ mua / đăng ký | 3% |
| BO-03 | Giữ chân học viên | Học viên trả phí hoạt động ≥ 3 ngày/tuần | 40% |
| BO-04 | Chứng minh hiệu quả phương pháp | Band thi khóa so với band đầu vào | Tăng đúng mục tiêu khóa ở ≥ 50% học viên hoàn thành |
| BO-05 | Kiểm soát chi phí AI | Chi phí AI / doanh thu | ≤ 10% |
| BO-06 | Giảm tải nhân viên hỗ trợ | Câu hỏi Cam tự giải quyết, không thành ticket | ≥ 60% |

Các số ở cột cuối là đề xuất của BA, chưa phải số liệu nghiệp vụ.

## 3. Đối tượng

MVP chỉ phục vụ **người luyện IELTS** (D-01), từ mất gốc tới mục tiêu 6.5–7.0. Các nhóm khác trong PRD 3 (học sinh phổ thông, Cambridge, giao tiếp) để sau MVP.

## 4. Phạm vi

### 4.1 Trong MVP (ra mắt một lượt, D-07)

| Nhóm năng lực | Nội dung | Nguồn |
|---|---|---|
| Tài khoản | Đăng ký Google / Email, xác thực email, quên mật khẩu, phiên đăng nhập | SRS FR-AUTH |
| Định hướng | Q&A tư vấn nhu cầu; **kiểm tra đầu vào** xác định level; gợi ý khóa | D-08, D-09 |
| Khóa học | Danh mục 6 khóa, học thử, video từ kết nối, phụ đề, popup từ vựng | SRS FR-LMS |
| Bài tập | 39 dạng Luyện tập PPS, Luyện thêm, các dạng IELTS 4 kỹ năng (chế độ Luyện tập, có Cam gợi ý) | D-13, Figma |
| Thi | Mô phỏng thi IELTS trên máy, bài thi riêng mỗi khóa, có giờ | D-10..D-12 |
| AI | Viết phát triển ý, chấm viết, chấm nói, phát âm từng từ, Trợ lý AI (Cam) | SRS FR-AI, Figma |
| Thương mại | Mua khóa 6/12 tháng, gói lượt AI, link kích hoạt; cổng thật làm sau cùng | D-02, D-06, D-18 |
| Giữ chân | Streak, XP, danh hiệu, thông báo đẩy | SRS FR-RET |
| Hỗ trợ | Chatbox Cam + ticket chat với nhân viên; Zalo, hotline | D-16 |
| Quản trị | CMS nội dung, ngân hàng câu hỏi, đề thi; phân quyền hybrid; màn nhân viên; báo cáo | D-15, SRS FR-ADM |
| Nền tảng | Web responsive; app Flutter iOS/Android song song | D-07 |

### 4.2 Ngoài MVP

- Khóa ngoài IELTS (TOEIC, Giao tiếp, Cambridge, phổ thông).
- Combo (D-03), hoàn tiền (D-04), thuê bao tự gia hạn.
- Đăng ký bằng số điện thoại.
- 21 nguyên tắc còn lại.

## 5. Mô hình kinh doanh

### 5.1 Sản phẩm bán

| Loại | Mô tả | Giá |
|---|---|---|
| Khóa học | 1 trong 6 khóa IELTS, thời hạn 6 hoặc 12 tháng, kèm ví lượt AI tuần | decisions.md mục 2; 12 tháng: BQ-01 |
| Gói lượt AI | 10, 20, 50, 100, 200, 500 lượt, vào pool riêng | BQ-03 |
| Link kích hoạt | Admin phát khóa miễn phí cho kiểm thử, khuyến mãi | FR-COM-04 |

### 5.2 Miễn phí

- Q&A tư vấn, kiểm tra đầu vào.
- Học thử Topic đầu của mỗi khóa (Q-03): xem video, làm bài tự chấm. Không dùng AI.
- Chatbox Cam.

### 5.3 Lượt AI

Xem decisions.md mục 3. Tóm tắt: mỗi khóa có ví tuần reset thứ Hai; hết ví tuần thì dùng pool mua thêm.

## 6. Bên liên quan và vai trò vận hành

| Vai trò | Việc trong hệ thống |
|---|---|
| Chủ dự án | Duyệt yêu cầu, thiết kế Figma, quyết định ưu tiên |
| Giám đốc | Xem doanh thu, báo cáo tổng |
| Kế toán | Đối soát đơn hàng, thanh toán, hóa đơn (BQ-12); chọn cổng thanh toán |
| Phòng Đào tạo / Biên tập viên | Soạn khóa, video, câu hỏi, đề thi, bảng quy đổi band, Q&A, FAQ cho Cam |
| Giáo viên | Chờ BQ-08 |
| Nhân viên hỗ trợ | Trả lời ticket, tra cứu học viên, xử lý lỗi thanh toán, cộng lượt bù |
| Admin hệ thống | Cấu hình role, quyền, sản phẩm, giá |
| Học viên | Học, luyện, thi, mua |

## 7. Giả định và ràng buộc

- Hạ tầng: server riêng của công ty, Cloudflare Tunnel, Docker Compose (CLAUDE.md).
- Thiết kế cho 1.000 user, sau đó load test (D-17, BQ-05).
- Đề thi, nội dung do Phòng Đào tạo tự soạn; không dùng nội dung có bản quyền bên thứ ba.
- Chấm nói dùng chung engine với `pps-education` (Q-25); kết quả nói chờ 10–30 s.
- Tài liệu nguồn PRD/SRS chỉ sửa khi chủ dự án cho phép (BQ-14).

## 8. Kịch bản quy mô năm đầu (đề xuất)

Giả định: giá trung bình ~6,7 triệu; tỷ lệ mua 2–4%; người dùng hoạt động mỗi ngày ~10–15% số đăng ký; đồng thời giờ cao điểm ~15–20% DAU.

| Kịch bản | Đăng ký | Trả phí | Doanh thu | DAU | Đồng thời cao điểm |
|---|---|---|---|---|---|
| Thận trọng | 3.000 | 60 | ~0,4 tỷ | ~150 | ~30 |
| Cơ sở | 10.000 | 300 | ~2 tỷ | ~800 | ~150 |
| Lạc quan | 30.000 | 1.200 | ~8 tỷ | ~3.000 | ~500 |

Mục tiêu tải **1.000 đồng thời** phủ cả kịch bản lạc quan. Điểm nghẽn dự kiến là chấm nói và AI, không phải API thường.

## 9. Rủi ro

| Mã | Rủi ro | Mức | Giảm thiểu |
|---|---|---|---|
| R-01 | Phạm vi MVP rất rộng (39 dạng bài, 4 kỹ năng thi, chấm nói, phát âm, chatbox, mobile) mà ra mắt một lượt | Cao | Bảng ưu tiên P0–P3 ([roadmap.md](roadmap.md)); cắt P3 nếu trễ |
| R-02 | Phòng Đào tạo chưa kịp soạn đủ nội dung, đề thi cho 6 khóa | Cao | Nội dung là đường găng riêng trong tiến độ; CMS và import làm sớm |
| R-03 | Chưa chốt cổng thanh toán | Trung bình | Cổng giả lập + link kích hoạt; interface `PaymentGateway` sẵn |
| R-04 | Chi phí AI vượt dự tính | Trung bình | Ví tuần theo khóa, rate limit, báo cáo chi phí AI |
| R-05 | Engine chấm nói dùng chung với `pps-education` quá tải giờ cao điểm | Trung bình | Q-25; hàng đợi, thông báo khi chấm xong |
| R-06 | Đề thi bị lộ, chia sẻ | Thấp | Ngân hàng nhiều đề, xáo câu, chỉ trả đáp án sau khi nộp |
| R-07 | Phát âm từng từ chạy trên máy chưa kiểm chứng (Q-32..36) | Trung bình | Xếp P2, thử nghiệm trên máy thật trước |
