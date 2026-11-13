# Use case và yêu cầu chức năng bổ sung (SRS bổ sung)

Phiên bản 0.2 · 2026-11-13 · Trạng thái: **Đã duyệt, FR đã chép vào SRS v1.1**

- Mã FR có nhãn **(mới)** hoặc **(sửa)** đã được chép vào `docs/SRS.md` v1.1. SRS giữ quy tắc; file này giữ luồng chính, luồng lỗi chi tiết. Lệch nhau thì SRS đúng.
- Các FR đã có trong SRS và đã làm ở backend (Auth, Catalog, LMS, bài tập 7 dạng, AI viết, đơn hàng, streak/XP) không viết lại; xem SRS mục 4 và `docs/system-design.md` mục 5.
- Quyết định `D-xx` và câu hỏi `BQ-xx`: [decisions.md](decisions.md).

## 0. Tác nhân

| Mã | Tác nhân | Ghi chú |
|---|---|---|
| A-GUEST | Khách chưa đăng nhập | |
| A-STU | Học viên (đã đăng nhập) | Chưa mua: học thử; đã mua: theo khóa còn hạn |
| A-CAM | Cam — trợ lý AI | Tác nhân hệ thống |
| A-SUP | Nhân viên hỗ trợ | |
| A-EDU | Biên tập viên / Phòng Đào tạo | Thay `EDITOR` hiện có |
| A-TEA | Giáo viên | Quyền chờ BQ-08 |
| A-ACC | Kế toán | |
| A-DIR | Giám đốc | |
| A-ADM | Admin hệ thống | |
| A-PAY | Cổng thanh toán | Chưa chốt (D-18) |
| A-AI | Dịch vụ AI ngoài (LLM, Whisper, engine chấm nói) | |

Từ A-SUP trở xuống là **nhân sự nội bộ**; mỗi người có một hoặc nhiều role, quyền thực tế theo bảng quyền (FR-ADM-04).

## 1. Danh mục FR bổ sung

| Mã | Tên | Loại | Ưu tiên |
|---|---|---|---|
| FR-GOAL-01 | Q&A tư vấn nhu cầu | (sửa) | P0 |
| FR-GOAL-03 | Gợi ý khóa theo nhu cầu + level | (sửa) | P0 |
| FR-TEST-01 | Kiểm tra đầu vào | (mới) | P0 |
| FR-TEST-02 | Kết quả kiểm tra đầu vào và level | (mới) | P0 |
| FR-EX-04 | 39 dạng bài Luyện tập PPS theo Figma | (mới) | P0–P2 theo dạng |
| FR-EX-05 | Dạng bài IELTS 4 kỹ năng, chế độ Luyện tập | (mới) | P1 |
| FR-EX-06 | Thời gian làm bài, tạm dừng và làm tiếp | (mới) | P0 |
| FR-EX-07 | Luyện thêm | (mới) | P1 |
| FR-EXAM-01 | Mô phỏng thi IELTS trên máy | (mới) | P1 |
| FR-EXAM-02 | Bài thi của khóa | (mới) | P1 |
| FR-EXAM-03 | Quy đổi band | (mới) | P1 |
| FR-AI-04 | Lượt AI theo tuần, theo khóa | (sửa) | P0 |
| FR-AI-06 | Cam gợi ý trong khi luyện tập | (mới) | P1 |
| FR-AI-07 | Trợ lý AI (Cam) hỏi đáp học tập | (mới) | P2 |
| FR-COM-05 | Gói lượt AI mua thêm (pool) | (mới) | P1 |
| FR-COM-06 | Gia hạn khóa | (mới) | P1 |
| FR-COM-07 | Quản lý sản phẩm và giá | (mới) | P0 |
| FR-SUP-01 | Chatbox Cam trả lời hỗ trợ | (mới) | P2 |
| FR-SUP-02 | Ticket và chat với nhân viên | (mới) | P1 |
| FR-SUP-03 | Công cụ nhân viên hỗ trợ | (mới) | P0 |
| FR-ADM-04 | Phân quyền hybrid (role + quyền) | (mới) | P0 |
| FR-ADM-05 | Nhật ký thao tác nội bộ | (mới) | P0 |
| FR-ADM-06 | Quản lý đề thi, đề đầu vào, bảng quy đổi | (mới) | P1 |
| FR-ADM-02 | Import câu hỏi Excel/Word | (đã có trong SRS, chưa làm) | P1 |
| NFR-MOB-01 | App Flutter dùng chung API, đủ tính năng học viên | (mới) | P1 |

Định nghĩa P0–P3: [roadmap.md](roadmap.md).

---

## 2. Định hướng: Q&A, kiểm tra đầu vào, gợi ý khóa

### UC-01 Làm Q&A tư vấn nhu cầu — FR-GOAL-01 (sửa)

- **Tác nhân:** A-GUEST, A-STU.
- **Mục đích:** biết học viên cần gì (mục tiêu, band mong muốn, thời gian học, kỹ năng yếu). **Không** dùng để xác định level (D-09).
- **Luồng chính:**
  1. Người dùng mở "Q&A gợi ý khóa học".
  2. Hệ thống hiện lần lượt các câu hỏi (bộ tạm Q-04, Phòng Đào tạo sửa được trong CMS).
  3. Người dùng trả lời từng câu, có thể quay lại câu trước.
  4. Hoàn tất: hệ thống lưu câu trả lời và chuyển sang UC-03 nếu đã có level, hoặc mời làm UC-02.
- **Luồng thay thế:**
  - 4a. Khách chưa đăng nhập: lưu ở trình duyệt; khi đăng ký thì ghi vào hồ sơ (Q-05).
  - 4b. Làm lại Q&A: ghi đè kết quả cũ.
- **Dữ liệu ra:** bộ câu trả lời → `user_goals` (`target_type`, `target_value`), thời gian học/tuần, kỹ năng muốn cải thiện.

### UC-02 Làm kiểm tra đầu vào — FR-TEST-01 (mới)

- **Tác nhân:** A-STU (A-GUEST: BQ-06).
- **Tiền điều kiện:** Phòng Đào tạo đã xuất bản ít nhất một đề đầu vào (UC-31).
- **Luồng chính:**
  1. Học viên bấm "Kiểm tra đầu vào".
  2. Hệ thống hiện hướng dẫn: số phần, thời gian, không được tạm dừng.
  3. Học viên bắt đầu; đồng hồ đếm ngược theo cấu hình đề (D-11).
  4. Học viên làm bài theo giao diện mô phỏng thi (UC-11).
  5. Học viên nộp, hoặc hết giờ thì hệ thống tự nộp.
  6. Hệ thống chấm (phần tự chấm ngay; Writing/Speaking nếu có thì qua AI, không trừ lượt) → UC-03.
- **Luồng lỗi:**
  - 3a. Mất mạng: câu đã chọn lưu tạm trên máy, gửi lại khi có mạng; đồng hồ vẫn chạy theo giờ máy chủ.
  - 3b. Đóng trình duyệt giữa chừng: mở lại trong thời gian còn lại thì làm tiếp; hết giờ thì nộp những gì đã lưu.
  - 5a. AI lỗi khi chấm Writing/Speaking: báo "đang chấm", chấm lại tự động; level tạm tính từ phần đã chấm.
- **Quy tắc:** miễn phí; số lần làm lại, cấu trúc đề: BQ-06.
- **Dữ liệu ra:** `placement_attempts` (đề, câu trả lời, điểm từng phần, band, level).

### UC-03 Xem kết quả và gợi ý khóa — FR-TEST-02, FR-GOAL-03 (sửa)

- **Tác nhân:** A-STU.
- **Luồng chính:**
  1. Hệ thống hiện band ước tính từng kỹ năng và level tổng.
  2. Hệ thống chọn khóa: khóa có **đầu vào ≤ band hiện tại < đầu ra**; nếu band mục tiêu (UC-01) cao hơn đầu ra thì gợi ý thêm các khóa kế tiếp theo thứ tự.
  3. Học viên bấm "Học thử" hoặc "Mua khóa".
- **Luồng thay thế:** 2a. Chưa có kết quả đầu vào → gợi ý theo trình độ tự đánh giá (FR-GOAL-01 cũ), kèm lời mời làm kiểm tra đầu vào.
- **Quy tắc:** bảng ánh xạ band → khóa lấy từ dải đầu vào/đầu ra của 6 khóa (decisions.md mục 2), Phòng Đào tạo sửa được.

---

## 3. Bài tập và luyện tập

### UC-10 Làm bộ bài tập Luyện tập PPS — FR-EX-04, FR-EX-06 (mới)

- **Tác nhân:** A-STU.
- **Tiền điều kiện:** có quyền vào Topic (học thử hoặc khóa còn hạn).
- **Luồng chính:**
  1. Học viên chọn bộ bài tập trong Topic.
  2. Hệ thống hiện câu theo dạng (39 dạng, mục 3.1), có đồng hồ nếu bộ có cấu hình thời gian.
  3. Học viên trả lời; với dạng có "nhánh thử lại" (Figma 10.01), sai lần đầu được thử lại kèm gợi ý.
  4. Hệ thống chấm: dạng tự chấm trả ngay (< 100 ms, FR-EX-03); dạng cần AI xếp hàng đợi và trừ lượt (FR-AI-04).
  5. Hiện Đúng / Cần sửa và lời giải thích từng câu.
  6. Kết thúc bộ: điểm tổng, cộng XP, cập nhật streak.
- **Luồng thay thế:**
  - 2a. Tạm dừng: lưu câu trả lời và thời gian còn lại; lần sau mở thì làm tiếp (chỉ chế độ Luyện tập, D-11).
  - 2b. Hết giờ: tự nộp.
- **Luồng lỗi:**
  - 4a. Hết lượt AI: câu cần AI không chấm; mời mua thêm lượt; các câu tự chấm vẫn có kết quả.
  - 4b. AI lỗi / quá hạn: giữ trạng thái "đang chấm", không trừ lượt khi lỗi, thông báo khi xong.
- **Dữ liệu vào/ra:** `student_exercise_attempts` (+ `paused_at`, `remaining_seconds`), `student_answers`, `student_answer_grading`.

#### 3.1 Danh sách dạng Luyện tập PPS (theo Figma, dữ liệu trong Figma là mẫu)

| Figma | Dạng | Cách chấm (đề xuất, chờ đối chiếu `pps-education`) |
|---|---|---|
| 10.01 | Chọn một đáp án | Tự động |
| 10.02 | Chọn từ trong câu | Tự động |
| 10.03 | Chọn nhiều đáp án | Tự động |
| 10.04 | Đúng hoặc sai | Tự động |
| 10.05 | Điền từ | Tự động (danh sách đáp án chấp nhận) |
| 10.06 | Điền từ theo nhóm | Tự động |
| 10.07 | Điền từ theo hình | Tự động |
| 10.08 | Chọn từ trong hộp | Tự động |
| 10.09 | Điền vào đoạn văn | Tự động |
| 10.10 | Sắp xếp câu | Tự động |
| 10.11 | Sắp xếp chữ cái | Tự động |
| 10.12 | Đề ảnh · Trắc nghiệm | Tự động |
| 10.13 | Đề ảnh · Điền từ | Tự động |
| 10.14 | Đọc hiểu · Lưới A–D | Tự động |
| 10.15 | Đọc hiểu · Đáp án chung A–J | Tự động |
| 10.16 | Đọc điền từ | Tự động |
| 10.17 | Đọc · Nhóm câu độc lập | Tự động |
| 10.18 | Nghe · Chọn đáp án chữ | Tự động |
| 10.19 | Nghe · Chọn hình | Tự động |
| 10.20 | Nghe · Chọn nhiều đáp án | Tự động |
| 10.21 | Nghe · Điền từ | Tự động |
| 10.22 | Nghe · Điền phiếu thông tin | Tự động |
| 10.23 | Nghe · Nộp audio | AI (nói) |
| 10.24 | Nghe · Nhóm câu | Tự động |
| 10.25 | Nghe ép | Tự động |
| 10.26 | Viết tự luận | AI (viết) |
| 10.27 | Nói · Tải bản ghi | AI (nói) |
| 10.28 | Video phản xạ · Câu ngắn | AI (nói) |
| 10.29 | Video phản xạ · Cue card | AI (nói) |
| 10.30 | Video phản xạ · Thảo luận | AI (nói) |
| 10.31 | Video phản xạ · Tả tranh | AI (nói) |
| 10.32 | Video từ nối | Tự động / theo dõi xem |
| 10.33 | Luyện nghe | Tự động |
| 10.34 | Chép chính tả | Tự động (so khớp) |
| 10.35 | Luyện nói | AI (nói) |
| 10.36 | Ghép hai vế | Tự động (= `CONNECTIVE_MATCH`) |
| 10.37 | Viết lại với từ nối | Luật + AI (= `GRAMMAR_REWRITE`) |
| 10.38 | Tìm và sửa lỗi | Tự động (= `GRAMMAR_SPOT_FIX`) |
| 10.39 | Lô bài tập | Bộ gộp nhiều dạng |

Ghi chú thiết kế: `pps-education` chỉ có 8 kiểu câu hỏi ở tầng dữ liệu (`MULTIPLE_CHOICE`, `MULTIPLE_ANSWER`, `TRUE_FALSE`, `FILL_IN_BLANK`, `WORD_BANK`, `SENTENCE_BUILDING`, `ESSAY`, `SPEAKING`) cộng cờ "Nghe ép"; 39 dạng trên phần lớn là **cách hiển thị** khác nhau của các kiểu đó (có đề ảnh, có audio, nhóm câu...). Đề xuất: dữ liệu theo kiểu chấm, giao diện theo dạng hiển thị → giảm nhiều việc backend. `pps-education` không có module chat/ticket; giới hạn thời gian dùng `time_limit_minutes` ở bài, máy chủ tự chốt bài quá giờ (job 5 phút) — dùng lại cách này cho FR-EX-06.

Lưu ý: SRS FR-EX-02 có "Nhận diện cấu trúc" và "Xếp khối tạo câu" (đã có code `GRAMMAR_IDENTIFY`, `GRAMMAR_BUILD`); cần chủ dự án xác nhận chúng ứng với dạng Figma nào (10.01, 10.10?).

### UC-11 Làm bài IELTS chế độ Luyện tập — FR-EX-05, FR-AI-06 (mới)

- **Tác nhân:** A-STU, A-CAM.
- **Luồng chính:**
  1. Học viên chọn kỹ năng (Reading, Listening, Writing, Speaking) và bài.
  2. Giao diện giống thi trên máy (Figma 00–06): đề bên trái, câu hỏi bên phải, thanh điều hướng câu, đánh dấu câu xem lại.
  3. Học viên trả lời; được tạm dừng và làm tiếp.
  4. Cam hiện bong bóng gợi ý theo dạng câu (Figma 07); gợi ý dài chuyển thành popup (Figma 09); học viên kéo Cam để thu gọn (Figma 08).
  5. Xem lại trước khi nộp → xác nhận kết thúc → nộp.
  6. Kết quả: Reading/Listening số câu đúng + band (FR-EXAM-03), giải thích từng câu; Writing/Speaking chấm AI (trừ lượt).
- **Quy tắc:** gợi ý của Cam ưu tiên soạn sẵn theo câu (memory hướng gợi ý); chỉ gọi AI khi chủ dự án duyệt.
- **Dạng câu IELTS:** Reading 15 dạng (Multiple choice một/nhiều đáp án, T/F/NG, Y/N/NG, Matching information/headings/features/sentence endings, Sentence/Summary/Note/Table/Flow-chart completion, Diagram label, Short-answer); Listening 11 dạng; Writing Task 1, Task 2; Speaking Part 1–3.

### UC-12 Luyện thêm — FR-EX-07 (mới)

- **Tác nhân:** A-STU.
- **Mô tả:** học viên chọn dạng bài (Figma 10.00 "Danh mục dạng bài") và luyện các câu ngoài lộ trình Topic.
- **Câu hỏi mở:** câu Luyện thêm lấy từ đâu (ngân hàng của khóa đã mua, hay ngân hàng riêng)? Có trừ lượt AI theo khóa nào? → thêm BQ khi chủ dự án xem.

---

## 4. Thi

### UC-20 Thi bài thi của khóa — FR-EXAM-01, FR-EXAM-02 (mới)

- **Tác nhân:** A-STU.
- **Tiền điều kiện:** khóa đã mua và còn hạn (D-10); bài thi đã xuất bản.
- **Luồng chính:**
  1. Học viên vào "Mô phỏng thi" của khóa, chọn kỹ năng (Figma 00.01).
  2. Hệ thống hiện thời gian, số câu, cảnh báo không tạm dừng.
  3. Làm bài; đồng hồ theo máy chủ; không có gợi ý Cam.
  4. Xem lại trước khi nộp (00.02) → xác nhận (00.04) → đã nộp (00.03).
  5. Chấm: R/L tự động + quy đổi band; W/S AI (trừ lượt ví tuần của khóa).
  6. Kết quả: band từng kỹ năng, band tổng, xem lại đáp án.
- **Luồng lỗi:** như UC-02 3a, 3b, 5a. Hết lượt AI lúc nộp Writing/Speaking: bài vẫn được lưu, chấm khi học viên có lượt (đề xuất), hoặc chặn từ bước 1 (chờ duyệt).
- **Quy tắc:** số lần thi, điều kiện mở: BQ-07. Đáp án chỉ trả sau khi nộp.

### UC-21 Quy đổi band — FR-EXAM-03 (mới)

- Reading/Listening: số câu đúng → band theo bảng do Phòng Đào tạo nhập (UC-31).
- Writing/Speaking: AI trả band theo 4 tiêu chí; band tổng = trung bình làm tròn theo quy tắc IELTS (0,25 → 0,5; 0,75 → lên 1).

---

## 5. Lượt AI và mua hàng

### UC-30 Trừ lượt AI theo khóa — FR-AI-04 (sửa)

- **Tác nhân:** hệ thống.
- **Luồng chính:**
  1. Trước khi gọi AI, xác định khóa của tác vụ và trọng số lượt (decisions.md mục 3).
  2. Ví tuần của khóa đủ → giữ chỗ lượt, gọi AI; xong thì trừ thật, ghi `ai_grading_token_usage`.
  3. Ví tuần thiếu → lấy phần còn thiếu từ pool mua thêm.
  4. Cả hai thiếu → từ chối, trả "hết lượt", hiện nút mua thêm.
- **Luồng lỗi:** AI lỗi → hoàn lượt đã giữ.
- **Quy tắc:** ví tuần nạp lại 00:00 thứ Hai (Asia/Ho_Chi_Minh), không cộng dồn; khóa hết hạn thì ví tuần dừng; giữ rate limit 5 req/phút (NFR-SEC-03).
- **Dữ liệu:** `course_ai_wallets` (enrollment, tuần, đã dùng, hạn mức), `ai_credit_pool` + sổ cái `ai_quota_ledger` (mọi cộng/trừ).

### UC-32 Mua gói lượt AI — FR-COM-05 (mới)

- **Tác nhân:** A-STU, A-PAY.
- **Luồng chính:** chọn mức (10/20/50/100/200/500) → tạo đơn → thanh toán (UC-34) → cộng vào pool, ghi sổ cái.
- **Quy tắc:** hạn dùng pool: BQ-03.

### UC-33 Mua / gia hạn khóa — FR-COM-01, FR-COM-06

- **Luồng chính:** chọn khóa → chọn 6 hoặc 12 tháng → tạo đơn → thanh toán → mở quyền, tạo ví tuần.
- **Gia hạn khi còn hạn:** BQ-02 (đề xuất cộng dồn).

### UC-34 Thanh toán — FR-COM-02 (xếp cuối, D-18)

- Tới khi chốt cổng: dùng cổng giả lập và link kích hoạt. Luồng chi tiết giữ như `docs/system-design.md` mục 5.6.

### UC-35 Quản lý sản phẩm và giá — FR-COM-07 (mới)

- **Tác nhân:** A-ADM (quyền `PRODUCT_MANAGE`).
- **Luồng:** tạo/sửa khóa bán (tên, khóa liên kết, thời hạn, giá, lượt/tuần), gói lượt (số lượt, giá), bật/tắt bán.
- **Quy tắc:** đổi giá không ảnh hưởng đơn đã tạo; mọi thay đổi ghi nhật ký (FR-ADM-05).

---

## 6. Hỗ trợ học viên

### UC-40 Hỏi Cam trong chatbox — FR-SUP-01 (mới)

- **Tác nhân:** A-STU (A-GUEST: chờ duyệt), A-CAM.
- **Luồng chính:**
  1. Học viên mở chatbox, gõ câu hỏi.
  2. Cam trả lời dựa trên FAQ do nội bộ soạn và dữ liệu tài khoản của chính học viên (khóa, hạn, đơn gần nhất, lượt còn lại).
  3. Học viên đánh dấu "đã giải quyết" hoặc bấm "Gặp nhân viên" → UC-41.
- **Luồng lỗi:** Cam không chắc / câu hỏi về tiền, lỗi thanh toán → tự đề nghị gặp nhân viên.
- **Quy tắc:** không trừ lượt AI; giới hạn tần suất riêng; Cam không được hứa hoàn tiền, cộng lượt, đổi khóa.
- **Câu hỏi:** BQ-09.

### UC-41 Ticket và chat với nhân viên — FR-SUP-02 (mới)

- **Tác nhân:** A-STU, A-SUP.
- **Luồng chính:**
  1. Hệ thống tạo ticket (trạng thái `OPEN`) kèm lịch sử chat với Cam.
  2. Nhân viên thấy ticket trong hàng chờ, nhận xử lý (`IN_PROGRESS`).
  3. Hai bên chat trong cùng chatbox (thời gian thực; học viên nhận thông báo đẩy khi có trả lời).
  4. Nhân viên đóng ticket (`RESOLVED`); học viên có thể mở lại trong 7 ngày (đề xuất).
- **Luồng thay thế:** ngoài giờ làm việc: báo thời gian phản hồi dự kiến, gợi ý Zalo/hotline.

### UC-42 Công cụ nhân viên — FR-SUP-03 (mới)

- **Tác nhân:** A-SUP.
- **Chức năng:** tra cứu học viên (email, tên, mã đơn); xem khóa, hạn, đơn, lượt, ticket; kích hoạt khóa thủ công khi lỗi thanh toán; cộng lượt bù; mở khóa tài khoản; gửi lại email xác thực.
- **Quy tắc:** mỗi thao tác thay đổi dữ liệu bắt buộc nhập lý do và ghi nhật ký (FR-ADM-05); không xem doanh thu tổng.

---

## 7. Quản trị

### UC-50 Phân quyền hybrid — FR-ADM-04 (mới)

- **Tác nhân:** A-ADM.
- **Mô hình** (giống `pps-education`, bản gọn): quyền là đơn vị nhỏ nhất, mã dạng `module.action` (vd `commerce.order.view`), nhóm theo module; role là tập quyền, lưu trong DB (không phải enum), tạo thêm role được; người dùng nội bộ có nhiều role, và có thể được **cấp thêm (GRANT) hoặc chặn (REVOKE)** từng quyền riêng, có ngày hết hạn tùy chọn. Quyền hiệu lực = hợp quyền các role − REVOKE còn hạn + GRANT còn hạn.
- **Bảng tham khảo từ `pps-education`:** `roles`, `permissions`, `role_permissions`, `user_roles`, `user_permission_overrides`, `permission_audit_log`. Bỏ phần không cần cho B2C: chức vụ (`positions`), phạm vi dữ liệu theo cơ sở/lớp (`data_scope`) — trừ khi Giáo viên cần giới hạn theo khóa (BQ-08).
- **Gọn hơn `pps-education`:** khoảng 30–40 quyền thay vì ~230; mỗi quyền có một câu mô tả tiếng Việt dễ hiểu; màn hình 3 phần: danh sách role, ma trận tick quyền theo nhóm, thành viên của role; quyền riêng từng người nằm ở trang chi tiết nhân sự.
- **Luồng chính:** tạo/sửa role → tick quyền theo nhóm (mỗi quyền có một câu mô tả ngắn) → gán role cho nhân sự → (tùy chọn) cấp thêm / chặn quyền riêng.
- **Quy tắc:** không được tự xóa quyền quản trị cuối cùng; thay đổi quyền có hiệu lực ở lần gọi API kế tiếp; ghi nhật ký.
- **Role mặc định đề xuất:** xem mục 7.1 (chờ BQ-08 và đối chiếu `pps-education`).

#### 7.1 Ma trận role mặc định (đề xuất)

| Nhóm quyền | Admin | Giám đốc | Kế toán | Hỗ trợ | Đào tạo | Giáo viên |
|---|---|---|---|---|---|---|
| Nội dung khóa, câu hỏi, đề thi | ✓ | xem | | | ✓ | xem |
| Sản phẩm, giá | ✓ | xem | xem | | | |
| Đơn hàng, thanh toán | ✓ | xem | ✓ | xem | | |
| Link kích hoạt | ✓ | | | ✓ | | |
| Học viên: tra cứu | ✓ | ✓ | ✓ | ✓ | | lớp/khóa mình (BQ-08) |
| Học viên: kích hoạt thủ công, cộng lượt, mở khóa | ✓ | | | ✓ | | |
| Ticket hỗ trợ | ✓ | xem | | ✓ | | |
| Báo cáo doanh thu | ✓ | ✓ | ✓ | | | |
| Báo cáo học tập, chi phí AI | ✓ | ✓ | | | ✓ | |
| Role, quyền, nhân sự | ✓ | | | | | |
| Nhật ký thao tác | ✓ | ✓ | | | | |

### UC-51 Nhật ký thao tác — FR-ADM-05 (mới)

- Ghi: ai, lúc nào, thao tác, đối tượng, giá trị trước/sau, lý do. Không sửa, không xóa được. Lọc theo người, đối tượng, ngày.

### UC-31 Quản lý đề thi, đề đầu vào, bảng quy đổi — FR-ADM-06 (mới)

- **Tác nhân:** A-EDU.
- **Luồng:** tạo đề → chọn loại (đầu vào / thi khóa / luyện tập) → chọn kỹ năng, phần, dạng câu → nhập câu hỏi, audio, bài đọc, hình → cấu hình thời gian từng phần, chế độ tạm dừng → nhập bảng quy đổi band → xem trước → xuất bản.
- **Quy tắc:** đề đã có người làm thì sửa đáp án phải chấm lại (đã có `RegradeController`); không xóa, chỉ ngừng xuất bản.

---

## 8. Mobile — NFR-MOB-01 (mới)

- App Flutter (iOS, Android) dùng chung REST API; **đủ tính năng phía học viên** như web; không có màn quản trị.
- Bắt buộc trước khi lên App Store: Apple Sign-in (Q-11); lưu refresh token trong secure storage (Q-13).
- Thông báo đẩy qua FCM (đã chốt trong CLAUDE.md).
- Phát âm từng từ (FR-AI-05) có thể làm trên mobile trước (Q-36).
