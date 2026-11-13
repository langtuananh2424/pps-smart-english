# Quyết định và câu hỏi mở (BA)

Nguồn: trao đổi BA với chủ dự án ngày 2026-11-13. File này ghi các quyết định **chưa có trong PRD/SRS**. PRD/SRS chỉ được sửa khi chủ dự án cho phép; tới lúc đó, mục nào ở đây lệch PRD/SRS thì ghi rõ ở cột "Lệch".

Mã: `D-xx` quyết định đã chốt, `BQ-xx` câu hỏi BA còn mở (khác `Q-xx` trong `docs/system-design.md`).

## 1. Quyết định đã chốt

| Mã | Quyết định | Lệch PRD/SRS | Ảnh hưởng code hiện có |
|---|---|---|---|
| D-01 | MVP chỉ bán **6 khóa IELTS online** (bảng mục 2). Không bán bản offline. Sản phẩm bán = một khóa học có thời hạn. | PRD 3, FR-TEST-02 (nhiều nhóm mục tiêu) thu hẹp còn IELTS | Dữ liệu `products`, `courses`; demo seed cần thay |
| D-02 | Giá trong bảng là giá **gói 6 tháng**, admin sửa được. | PRD 7 #4 trả lời một phần | Không |
| D-03 | Không bán combo. | FR-COM-01, FR-GOAL-03 nhắc combo | Bỏ loại sản phẩm `COMBO` khỏi MVP |
| D-04 | Không hoàn tiền. | Chưa có trong tài liệu | Không |
| D-05 | **Lượt AI theo tuần, theo từng khóa** (mục 3). | FR-AI-04, PRD FR-AUTH-02 | `AiQuotaService`, `UserAiQuota` đang là ví chung một tài khoản → phải sửa |
| D-06 | **Gói mua thêm lượt** là pool riêng, các mức 10, 20, 50, 100, 200, 500 lượt. | FR-COM-01 (Add-on) chi tiết hơn | Sản phẩm `AI_ADDON` |
| D-07 | Ra mắt **một lượt** khi web xong đủ tính năng; **app Flutter làm song song**; chấp nhận web ra muộn hơn. Sau ra mắt là pilot rồi bảo trì. | SRS 1.2 (mobile giai đoạn 2) | `CLAUDE.md` ghi "Mobile chỉ giữ khung" → cần chủ dự án cho sửa |
| D-08 | **Kiểm tra đầu vào có trong MVP**, miễn phí, dùng để xác định level. | Đảo PRD FR-TEST-01 (hoãn) | Module mới |
| D-09 | **Q&A tư vấn tách khỏi kiểm tra đầu vào.** Q&A xác định nhu cầu; kiểm tra đầu vào xác định level. Bộ Q&A bản tạm (Q-04) dùng cho MVP. | FR-GOAL-01..03 | Quy tắc đề xuất khóa = nhu cầu + level |
| D-10 | **Mỗi khóa có bài thi riêng**, phải mua khóa mới được thi. Đề do Phòng Đào tạo soạn (không dùng đề sách có bản quyền). | Chưa có trong tài liệu | Module mới |
| D-11 | Thời gian làm bài **cấu hình theo từng đề / bộ bài tập**. Luyện tập: được tạm dừng và làm tiếp. Kiểm tra / thi: không tạm dừng. | Chưa có | `question_banks` thêm thời gian, chế độ |
| D-12 | Chấm thi: Reading, Listening đổi số câu đúng sang band theo bảng quy đổi; Writing, Speaking AI chấm band và trừ lượt. | Chưa có | Bảng quy đổi band |
| D-13 | **Dạng bài theo Figma** (phần lớn lấy từ `pps-education`): 39 dạng "Luyện tập PPS" và các dạng IELTS 4 kỹ năng. Mỗi dạng IELTS có 2 chế độ: Mô phỏng thi và Luyện tập (có Cam gợi ý). | FR-EX-01..03 chỉ có 7 dạng | `QuestionType` cần mở rộng |
| D-14 | **Dữ liệu trong Figma là mẫu** (tên khóa, đề, đáp án...). Chỉ lấy bố cục và hành vi, không lấy dữ liệu. | — | — |
| D-15 | **Phân quyền hybrid** như `pps-education` (role + quyền), giao diện và mô tả quyền gọn hơn. Role gồm ít nhất: Admin, Giám đốc, Kế toán, Nhân viên hỗ trợ, Giáo viên, Biên tập viên. | SRS 2.2 chỉ có Admin/Content Creator | Hiện có `ADMIN`, `EDITOR` cố định → chuyển sang bảng quyền |
| D-16 | **Hỗ trợ học viên**: Zalo, hotline, và chatbox trong app. Cam (AI) trả lời câu đơn giản trước; học viên yêu cầu gặp nhân viên thì tạo ticket và chat với nhân viên trong cùng chatbox. | Chưa có | Module mới |
| D-17 | Thiết kế hệ thống cho **1.000 user**, sau đó chủ dự án chạy load test. Xem BQ-05. | NFR-PERF-01 | Kế hoạch load test |
| D-18 | Cổng thanh toán **chưa chốt**, chờ Kế toán. Xếp xuống cuối danh sách việc; tới lúc đó dùng cổng giả lập. | FR-COM-02 | Giữ `FakePaymentGateway` |
| D-19 | Mật khẩu tối thiểu 8 ký tự, không bắt buộc chữ hoa / số / ký tự đặc biệt. | Đóng Q-28 | Khớp `PasswordRules` (cần kiểm) |
| D-20 | Các màn hình còn thiếu (thanh toán, hồ sơ, admin, nhân viên, hỗ trợ) chủ dự án thiết kế dần trong Figma. | — | FE chờ Figma từng màn |

## 2. Danh mục khóa MVP (D-01, D-02)

| # | Tên khóa | Đầu vào → đầu ra | Giá gói 6 tháng (VNĐ) |
|---|---|---|---|
| 1 | Begin | 0 → 3.0 | 5.600.000 |
| 2 | Academic English 1 | 3.0 → 4.0 | 4.200.000 |
| 3 | Academic English 2 | 4.0 → 5.0 | 4.200.000 |
| 4 | Pre IELTS | 5.0 → 5.5 | 7.350.000 |
| 5 | IELTS Foundation | 5.5 → 6.0 | 11.025.000 |
| 6 | Super IELTS | 6.0 → 6.5/7.0 | 7.875.000 |

Bảng giá tham khảo, có thể điều chỉnh. Giá gói 12 tháng: BQ-01.

## 3. Mô hình lượt AI (D-05, D-06)

- Đơn vị: **lượt AI**, không hiển thị token cho học viên. Token vẫn ghi ở `ai_grading_token_usage` để tính chi phí.
- **Ví tuần**: mỗi khóa đang còn hạn có một ví riêng, nạp lại đủ mức mỗi **00:00 thứ Hai giờ Việt Nam**; lượt thừa không chuyển sang tuần sau.
- Tác vụ AI phát sinh trong khóa nào thì trừ ví tuần của khóa đó.
- **Pool mua thêm**: thuộc tài khoản, không reset theo tuần; chỉ trừ khi ví tuần của khóa đang dùng đã hết.
- Thứ tự trừ: ví tuần của khóa → pool mua thêm → báo hết lượt, mời mua thêm.
- Trọng số tạm (chờ BQ-04): viết lại câu 1; IELTS Writing Task 1: 2; Task 2: 3; mỗi phần Speaking: 2.
- Tác vụ AI ngoài khóa (bài kiểm tra đầu vào miễn phí, Cam trả lời hỗ trợ): không trừ lượt, chống lạm dụng bằng giới hạn tần suất (BQ-06, BQ-09).

## 4. Câu hỏi BA còn mở

| Mã | Câu hỏi | Chặn phần |
|---|---|---|
| BQ-01 | Giá gói 12 tháng của từng khóa? | Sản phẩm, trang mua |
| BQ-02 | Mua lại / gia hạn khi khóa còn hạn: cộng dồn thời hạn (đề xuất) hay tính lại từ ngày mua? | Kích hoạt |
| BQ-03 | Giá từng mức gói lượt (10…500)? Pool có hạn dùng không? Đề xuất: 12 tháng kể từ ngày mua. | Sản phẩm `AI_ADDON` |
| BQ-04 | Mỗi khóa được bao nhiêu lượt/tuần? Duyệt trọng số ở mục 3? | Quota |
| BQ-05 | "1.000 user" là 1.000 người **dùng đồng thời** (như NFR-PERF-01) hay 1.000 tài khoản? | Load test, cấu hình server |
| BQ-06 | Kiểm tra đầu vào: gồm kỹ năng nào, bao nhiêu câu, bao lâu? Có Writing/Speaking (tốn AI) không? Khách chưa đăng nhập có làm được không? Được làm lại bao nhiêu lần? Quy tắc đổi điểm → khóa gợi ý? | Module kiểm tra đầu vào |
| BQ-07 | Bài thi của khóa: được thi mấy lần? Mở khi nào (bất cứ lúc nào hay sau khi học xong X%)? Có cấp chứng nhận không? | Module thi |
| BQ-08 | Danh sách role chính thức và việc từng role làm. Giáo viên làm gì trong B2C (chấm tay, trả lời câu hỏi, soạn đề)? | Phân quyền |
| BQ-09 | Chatbox: Cam trả lời dựa trên nguồn nào (FAQ do ai soạn)? Giờ làm việc và thời gian phản hồi cam kết của nhân viên? Ticket có cần trạng thái, phân công, đánh giá không? | Module hỗ trợ |
| BQ-10 | Học thử áp dụng thế nào cho 6 khóa (mặc định 1 Topic đầu, Q-03)? Học thử có được làm bài thi khóa không? (D-10: không) | Quyền truy cập |
| BQ-11 | Khóa hết hạn: học viên còn xem được lịch sử bài làm, kết quả không? | Quyền truy cập |
| BQ-12 | Có xuất hóa đơn VAT / hóa đơn điện tử không? Kế toán cần báo cáo gì? | Thương mại, báo cáo |
| BQ-13 | Khi nguồn lực web và mobile giành nhau, ưu tiên bên nào? | Kế hoạch |
| BQ-14 | Cho phép sửa `CLAUDE.md` (mobile làm song song, kiểm tra đầu vào trong MVP) và cập nhật PRD/SRS theo các quyết định trên? | Tài liệu nguồn |

Câu hỏi mở từ `docs/system-design.md` vẫn còn hiệu lực: Q-07 (giấy phép từ điển), Q-09/Q-27 (gộp vào BQ-04), Q-13 (mobile lưu token, nay cần vì D-07), Q-16, Q-17, Q-22, Q-25 (chấm nói), Q-23 (giám sát), Q-29 (đường truyền video), Q-32..Q-36 (phát âm từng từ).

## 5. Bảng lệch PRD ↔ SRS ↔ thực tế

| Chủ đề | PRD | SRS | Thực tế đã chốt |
|---|---|---|---|
| Mã nhóm mục tiêu | FR-TEST-01..03 | FR-GOAL-01..03 | Dùng FR-GOAL; FR-TEST dành cho kiểm tra đầu vào |
| FR-AUTH-02 | Cấp tài khoản | JWT, phiên | Theo SRS |
| Cây nội dung | Level → Topic → Unit | Course → Level → Topic → Lesson | Theo SRS (code đã làm) |
| Lượt AI khi đăng ký | 3 lượt | 3 token | Không cấp; chỉ có khi mua (Q-27) |
| Đăng ký | Google, Apple, SĐT | Google, Apple, Email/SĐT | Google + Email; Apple trước khi lên App Store |
| Lưu media | R2 / YouTube | R2 / YouTube | MinIO + YouTube |
| Giờ nhắc streak | — | trước 21h | 20:00 (Q-20) |
| Kiểm tra đầu vào | Hoãn | Không bắt buộc | Có trong MVP (D-08) |
| Mobile | Giai đoạn 2 | Giai đoạn 2 | Song song (D-07) |
| Thanh toán | VietQR / VNPay / MoMo | PayOS / VNPay / MoMo | Chưa chốt (D-18) |
