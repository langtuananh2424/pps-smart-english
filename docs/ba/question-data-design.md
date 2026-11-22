# Thiết kế dữ liệu: dạng bài mới, ảnh, âm thanh, nhóm câu, Nghe ép, thống kê

Trạng thái: **đã duyệt (D-138), chưa code.** Ngày 2026-11-23. SRS đã cập nhật v1.5.
Mã yêu cầu: FR-ADM-02 (nhập/soạn câu hỏi), FR-EX-04, FR-EX-05, FR-EX-08, FR-EX-09, FR-EX-10 (PRD v0.4); D-133..D-137 (`decisions.md`).
Tài liệu liên quan: `question-editor-spec.md` (giao diện), `docs/system-design.md`. Lệch SRS thì SRS đúng; phần cần sửa SRS ghi ở mục 9.

## 1. Hiện trạng (đã đọc code)

| Thứ đã có | Ghi chú |
|---|---|
| `questions` (V4): 10 kiểu, `prompt` text thuần, `payload`/`answer_key` JSON, `skill` chỉ LISTENING/READING/GRAMMAR | Chưa có ảnh, audio, đoạn đọc chung, HTML |
| `question_banks`: `mode` PRACTICE/EXAM, `time_limit_sec` | Chưa có giới hạn số lần nghe, chế độ ẩn đáp án |
| `student_exercise_attempts` + `student_answers` (`is_correct`, `credit`, `answer`) | **Đã lưu đúng/sai từng câu theo từng lượt làm**; chưa có thời gian từng câu |
| `ObjectStorage` (MinIO): presigned POST có giới hạn dung lượng/loại, presigned GET có hạn, `head`, `readHead`, `delete` | Dùng lại cho ảnh và audio, không viết kho mới |
| `ResultAssembler`: trả đáp án + giải thích sau khi nộp | Cần thêm chế độ chỉ trả số câu đúng |
| `assignments`, `student_exercise_attempts.assignment_id` (V23) | Nơi giáo viên cấu hình Nghe ép khi giao bài |

Chưa đọc: cách `pps-education` làm cờ "Nghe ép" (tài liệu BA chỉ nói là một cờ). Thiết kế dưới đây theo mô tả của chủ dự án, không theo code cũ.

## 2. Nguyên tắc

1. **Dữ liệu theo kiểu chấm, giao diện theo dạng hiển thị** (use-cases 3.1): 39 dạng Figma và 26 dạng IELTS Reading/Listening dùng chung một số ít kiểu chấm. Thêm 5 kiểu chấm mới, không thêm 65 kiểu.
2. Không đổi tên, không xóa kiểu và cột hiện có; chỉ thêm. Migration mới từ **V26** (V1–V25 đã dùng), không sửa file đã push.
3. Đáp án không bao giờ ra client trước khi nộp (giữ NFR hiện có); chế độ Nghe ép ẩn cả sau khi nộp.
4. **Mọi con số nghiệp vụ là cấu hình, có mặc định, không hardcode** (D-138): số lần nghe, % đạt, N, số lần sai, giới hạn ảnh. Giá trị mặc định nằm ở cấu hình, người tạo đề/giáo viên ghi đè được.
5. Không tạo bảng tổng hợp thống kê ở giai đoạn đầu; tính khi truy vấn từ `student_answers`, thêm chỉ mục. Chỉ tạo bảng tổng hợp khi đo thấy chậm (có test tải theo checklist P0).

## 3. Kiểu chấm mới

Chỉ thêm giá trị vào `questions.type` (cột CHECK, mở rộng theo cách V11/V12 đã làm: H2 và PostgreSQL xử lý tên ràng buộc khác nhau).

| Kiểu mới | Chấm | Phủ các dạng |
|---|---|---|
| `CHOICE_MULTI` | Chọn nhiều đáp án; chấm từng phần hoặc cả câu (cấu hình) | MCQ chọn nhiều (IELTS R/L), `MULTIPLE_ANSWER` của pps-education |
| `TRUE_FALSE` | Đúng/Sai (`TF`), T/F/NG, Y/N/NG: tập lựa chọn nằm trong `payload.options` | T/F/NG, Y/N/NG, `TRUE_FALSE` |
| `FILL_TEXT` | Gõ chữ vào ô; so khớp không phân biệt hoa thường, nhiều đáp án chấp nhận, giới hạn số từ | Sentence/Summary/Note/Table/Flow-chart completion, Short answer, Diagram label (đi kèm ảnh) |
| `WORD_BANK` | Chọn từ trong hộp từ cho từng chỗ trống; từ có thể dùng lại hoặc không | Summary completion có hộp từ, `WORD_BANK` |
| `MATCH_OPTIONS` | Mỗi mục chọn một lựa chọn từ danh sách chung (A–H…) | Matching headings/information/features/sentence endings. `CONNECTIVE_MATCH` giữ nguyên cho nối hai vế |

Mỗi kiểu thêm một `QuestionGrader` và một validator trong `exercise/grading`, `exercise/validation` theo mẫu hiện có (GraderRegistry). Dạng hiển thị (có ảnh, có audio, có đoạn đọc) không phải kiểu mới: nó đến từ media và nhóm câu ở mục 4–5.

`skill` mở rộng thêm `WRITING`, `SPEAKING` (hiện chỉ có 3 giá trị) để hồ sơ năng lực (FR-EX-09) phủ đủ 4 kỹ năng.

## 4. Nội dung giàu định dạng và ảnh

### 4.1. Văn bản (TipTap)

- Thêm cột HTML cạnh cột text hiện có, không thay: `questions.prompt_html text`, `question_choices.content_html text` (null = chỉ dùng cột text).
- Cột text cũ **vẫn bắt buộc** và do máy chủ sinh từ HTML (bỏ thẻ). AI chấm, tìm kiếm, kiểm tra trùng và dữ liệu cũ đều dùng cột này.
- **Máy chủ làm sạch HTML** bằng danh sách thẻ cho phép (đoạn, in đậm/nghiêng/gạch chân, danh sách, bảng, ảnh nội bộ). Không tin HTML từ client (NFR-SEC, chống XSS). Thư viện: OWASP Java HTML Sanitizer (chưa kiểm tra tương thích phiên bản, chọn lúc code).
- Ảnh trong HTML chỉ trỏ tới `media_assets` của hệ thống (thẻ `data-asset-id`), không nhận URL ngoài.

### 4.2. Bảng `media_assets`

| Cột | Kiểu | Ghi chú |
|---|---|---|
| id | bigserial PK | |
| kind | varchar(10) | `IMAGE`, `AUDIO` |
| storage_key | varchar(300) | key trên MinIO, không bao giờ trả cho client |
| mime | varchar(60) | ảnh luôn `image/webp` sau xử lý |
| size_bytes | bigint | |
| width, height | integer | ảnh |
| duration_ms | integer | audio |
| uploaded_by | bigint FK users | |
| created_at | timestamptz | |

### 4.3. Quy trình ảnh (D-134)

1. Client xin presigned POST vào key tạm (`tmp/…`), tối đa 10 MB, loại `image/jpeg|png|webp|gif`.
2. Client gọi xác nhận; máy chủ đọc `readHead` kiểm tra đúng định dạng ảnh, giải mã.
3. Bỏ EXIF, thu nhỏ **cạnh dài ≤ 1600 px** (không phóng to), đổi WebP: chất lượng 80; ảnh chữ/biểu đồ dùng lossless; nếu vẫn > 300 KB thì hạ chất lượng xuống 70.
4. Ghi key chính thức, tạo `media_assets`, xóa key tạm. **Chỉ giữ bản đã xử lý.**
5. Dọn key tạm không được xác nhận sau 24 giờ (job có sẵn mẫu: `SpeakingCleanupJob`).

Thư viện WebP cho Java: chưa chọn, chưa kiểm chứng (ứng viên: Scrimage có WebP, hoặc gọi `cwebp` trong container). Quyết định lúc code, kèm test với ảnh thật. Xử lý ảnh chạy trong luồng giới hạn kích thước (timeout, giới hạn pixel đầu vào chống ảnh bom nén).

### 4.4. Gắn ảnh vào câu: `question_media`

| Cột | Ghi chú |
|---|---|
| question_id FK, asset_id FK | |
| role | `INLINE` (trong HTML), `DIAGRAM` (ảnh nền cho Diagram label), `OPTION` (ảnh của lựa chọn) |
| display_size | `SM` 33%, `MD` 50%, `LG` 75%, `FULL` 100% khung; **mỗi ảnh mỗi câu một cỡ** |
| alt_text | bắt buộc cho ảnh nội dung (accessibility) |
| sort_order | |

Một ảnh dùng lại ở nhiều câu với cỡ khác nhau nên cỡ nằm ở bảng nối, không ở `media_assets`.

## 5. Nhóm câu (đoạn đọc / audio dùng chung)

Bảng `question_groups`: bài đọc hoặc audio dùng chung cho nhiều câu (IELTS Reading passage, Listening part).

| Cột | Ghi chú |
|---|---|
| id, bank_id FK, sort_order | |
| title | ví dụ "Part 1" |
| instruction | hướng dẫn chung ("Choose NO MORE THAN TWO WORDS") |
| passage_html | bài đọc (đã làm sạch) |
| audio_asset_id FK null | audio của part |
| transcript_html | script của part, dùng cho gợi ý Nghe ép (mục 7.4); không trả client nếu chưa được phép |

`questions.group_id` nullable FK. Câu không thuộc nhóm hoạt động như hiện nay. Thứ tự hiển thị: nhóm theo `sort_order`, câu trong nhóm theo `questions.sort_order`.

Script theo từng câu: `questions.transcript_excerpt text` (null = dùng đoạn của nhóm). Để sau (chưa cần cho bản đầu) mốc thời gian audio theo câu.

## 6. Âm thanh và số lần nghe (D-135)

Cấu hình **nằm ở nhóm hoặc bộ câu**, không ở từng câu.

- `question_banks.playback_mode` varchar: `FREE` (mặc định), `EXAM` (tự phát, học viên không thao tác), `LOCKED` (Nghe ép: không tua, nghe lại sau khi hết). `mode=EXAM` hiện có ngầm định `EXAM` cho audio; để dễ hiểu vẫn lưu tường minh.
- `play_limit` (số lần nghe tối đa; null = không giới hạn) nằm ở **luật Nghe ép** (mục 7) hoặc `question_groups.play_limit` cho chế độ Thi. Chủ dự án: số lần nghe cấu hình được.

Đếm lượt nghe phía máy chủ, bảng `audio_plays`:

| Cột | Ghi chú |
|---|---|
| attempt_id FK, group_id FK, play_no | UNIQUE (attempt_id, group_id, play_no) |
| started_at, completed_at null | `completed_at` do client báo khi audio hết; có hạn, chỉ để cho phép "nghe lại" |

Mỗi lần phát: client xin `POST /api/attempts/{id}/groups/{gid}/play`, máy chủ kiểm `play_no ≤ play_limit` rồi cấp presigned GET TTL ngắn (độ dài audio + dư). Hết lượt thì từ chối. Không tua là khóa ở giao diện. **Chấp nhận (chủ dự án): người cố ý vẫn tải được file audio.** Không chặn tuyệt đối.

## 7. Nghe ép (FR-EX-10, D-136)

### 7.1. Hai tầng cấu hình

- **Người tạo đề** (CMS): bật `question_banks.forced_listening_allowed` cho bộ Listening hỗ trợ phương pháp; có `playback_mode`, `play_limit` mặc định.
- Bảng `forced_listening_rules`: người tạo đề đặt luật mặc định của bộ; giáo viên ghi đè khi giao bài. Học viên tự học dùng luật của bộ.

| Cột | Ghi chú |
|---|---|
| id, bank_id FK, assignment_id FK null | null = luật mặc định của bộ; có giá trị = luật của bài giao |
| play_limit | số lần nghe (mặc định 3) |
| pass_pct_total | ngưỡng cả bài, tính theo % số câu đúng (mặc định 70) |
| hint_every_n | N: cứ N lượt làm thì hiện danh sách câu sai nhiều (mặc định 3) |
| wrong_threshold | số lần sai để một câu bị coi là "sai nhiều" (mặc định 3) |

`forced_listening_group_thresholds (rule_id, group_id, pass_pct)`: ngưỡng % theo từng part.

### 7.2. Lượt làm

`student_exercise_attempts` thêm: `result_mode` (`FULL` | `COUNT_ONLY`, sao chép lúc bắt đầu từ luật), `rule_id` null. Sau khi nộp:

- `FULL`: như hiện nay (đáp án, giải thích).
- `COUNT_ONLY`: chỉ trả tổng số câu đúng, số câu đúng từng part, đã đạt ngưỡng chưa. Không trả `is_correct` từng câu, đáp án, giải thích.

Chưa đạt: học viên bắt đầu lượt mới (không giới hạn). Đạt hay không **không ảnh hưởng mở unit kế** (D-136, D-122): không thêm bất kỳ ràng buộc nào vào `UnitUnlockService`/`BankGate`.

### 7.3. Đếm câu sai

Không thêm bảng. Số lần sai của câu Q do học viên U trong bộ B (và bài giao A nếu có) = số dòng `student_answers` có `is_correct = false` thuộc các lượt đã nộp của U trên B. Chỉ mục: `student_answers (question_id, attempt_id)` đã có qua ràng buộc UNIQUE; thêm `student_exercise_attempts (user_id, bank_id, submitted_at)`.

Câu bị sửa đáp án (`answer_version` tăng) thì chỉ đếm các lượt cùng phiên bản, nếu không lần sai cũ do đáp án cũ sẽ tính oan. Cần `student_answers.answer_version` (cột mới, mặc định lấy từ câu lúc nộp); để thiết kế chi tiết với RegradeService đã có.

### 7.4. Hiển thị script

Số lượt đã nộp của U trên B là K. Khi `K` chia hết cho `hint_every_n` và `K > 0`, màn kết quả kèm danh sách câu có số lần sai ≥ `wrong_threshold`. Học viên chọn câu muốn xem script; máy chủ trả `transcript_excerpt` (hoặc `transcript_html` của nhóm) của câu đó. Ghi `script_views (user_id, question_id, attempt_id, viewed_at)` để thống kê và để giáo viên biết (không giới hạn số câu được xem; không có cơ chế gợi ý theo tầng: chủ dự án chưa chọn).

Script **không bao giờ** nằm trong phản hồi bài làm thông thường; chỉ qua `GET /api/attempts/{id}/scripts/{questionId}`, kiểm điều kiện trên ở máy chủ.

Sau này chuyển thành Cam gợi ý (D-136): đổi nguồn hiển thị, giữ nguyên dữ liệu.

## 8. Lưu quá trình làm bài và thống kê (FR-EX-08, FR-EX-09, D-137)

### 8.1. Bổ sung dữ liệu

| Thay đổi | Mục đích |
|---|---|
| `student_answers.time_spent_ms integer null` | Thời gian làm từng câu, client báo, máy chủ chặn giá trị phi lý (0..3600000) |
| `student_answers.answer_version integer` | Mục 7.3 |
| `questions.skill` mở rộng 5 giá trị | Mục 3 |
| Chỉ mục `student_answers (question_id)`, `student_exercise_attempts (user_id, submitted_at)` | Thống kê theo học viên và theo câu |

Không cần bảng mới cho FR-EX-08: `student_answers` đã lưu đáp án, đúng/sai, điểm từng phần theo từng lượt.

### 8.2. Thống kê tính khi truy vấn

Chiều thống kê: `skill`, `questions.type`, `question_grammar_tags` (yếu tố ngữ pháp), `topic`. Chỉ số: số câu đã làm, tổng `credit`, tỉ lệ đúng, xu hướng theo tuần. Câu dạng AI (`is_correct` null) lấy điểm từ `student_answer_grading` và hiển thị riêng (band/điểm), không trộn vào tỉ lệ đúng.

- Màn kết quả cuối bài: `GET /api/attempts/{id}/result` mở rộng: số đúng/sai, danh sách câu sai kèm số lần sai tích lũy của học viên (khi `result_mode=FULL`), phân rã theo kỹ năng/dạng/yếu tố ngữ pháp của chính lượt đó.
- Hồ sơ năng lực học viên: `GET /api/me/learning-profile`; giáo viên xem học viên trong lớp: `GET /api/classes/{classId}/students/{userId}/learning-profile` qua `ClassAccess` (NFR-SEC-05: không thuộc lớp thì 404).
- "Gợi ý cần cải thiện" (FR-EX-09): giai đoạn đầu dùng luật (kỹ năng/yếu tố có tỉ lệ đúng thấp nhất, đủ số câu tối thiểu), không gọi AI.

## 9. Nhập hàng loạt, hoàn tác, trùng câu (D-133)

- `import_batches (id, bank_id, user_id, source, status, created_at, undone_at)`; `questions.import_batch_id` nullable.
- **Hoàn tác** luôn được (D-138). Câu chưa có lượt làm thì xóa; câu đã có lượt làm thì ẩn bằng `questions.archived_at` (giữ lịch sử học viên, không hiện cho lượt làm mới). Nhập thêm và sửa câu vẫn được khi đã có lượt làm.
- **Trùng câu**: `questions.prompt_hash` (SHA-256 của text đã chuẩn hóa: bỏ thẻ, hạ chữ thường, gộp khoảng trắng) + chỉ mục `(bank_id, prompt_hash)`; so khớp mờ (gần giống) để sau.
- Bước xem trước của luồng nhập trả danh sách lỗi từng dòng và các câu bị nghi trùng (trong cùng bộ và các bộ khác cùng topic).

## 10. Danh sách migration đề xuất

| File | Nội dung |
|---|---|
| V26 | `media_assets`, `question_media`, `question_groups`, `questions.group_id`, `prompt_html`, `question_choices.content_html`, `transcript_excerpt`, `prompt_hash`, `archived_at`, `import_batch_id`, `import_batches` |
| V27 | Mở rộng CHECK `questions.type` (5 kiểu mới) và `questions.skill` (cách V11/V12: tệp chung + tệp riêng PostgreSQL) |
| V28 | Bộ câu và Nghe ép: `playback_mode`, `forced_listening_allowed`, `forced_listening_rules`, `…_group_thresholds`, `audio_plays`, `script_views`, `attempts.result_mode/rule_id` |
| V29 | Thống kê: `student_answers.time_spent_ms`, `answer_version`, chỉ mục |

Mỗi V có đường lùi tương thích ngược (cột thêm đều null được hoặc có mặc định), phù hợp P0 "migration tương thích ngược".

## 11. Thứ tự làm đề xuất (mỗi bước một issue, một commit logic)

1. Media: `media_assets`, upload ảnh, xử lý WebP, gắn ảnh vào câu (V26 phần media).
2. Nhóm câu + văn bản giàu định dạng (làm sạch HTML).
3. 5 kiểu chấm mới + validator + test.
4. Thống kê: `time_spent_ms`, màn kết quả cuối bài, hồ sơ năng lực (FR-EX-08/09).
5. Audio, đếm lượt nghe, Nghe ép (FR-EX-10).
6. Nhập hàng loạt: dán nhanh, Excel, hoàn tác, trùng câu; nhập Word sau.

Giao diện soạn (Figma) làm song song từ `question-editor-spec.md`.

## 12. Đã chốt (2026-11-23)

1. Sửa SRS: đã làm (v1.5).
2. Nghe ép do người tạo đề bật và đặt số; mặc định số lần nghe 3, ngưỡng 70%, N 3, sai nhiều từ 3 lần; tất cả chỉnh được.
3. Chế độ Thi: số lần nghe do người tạo đề đặt (mặc định đề xuất 1, chỉnh được).
4. Hoàn tác lô được cả khi có lượt làm (ẩn câu).
5. Số liệu ảnh được duyệt: 1600 px, chất lượng 80, dưới 300 KB, 4 cỡ hiển thị. Có thể đưa vào cấu hình.
6. Còn lại lúc code: thư viện WebP (Scrimage hay `cwebp`).

## 13. Điều chưa kiểm chứng

- Chưa chạy V14–V25 trên PostgreSQL thật; các migration mới cũng chỉ thử trên H2 cho đến khi có.
- Chưa đo hiệu năng truy vấn thống kê trên dữ liệu lớn; dựa vào chỉ mục và quyết định bảng tổng hợp theo số đo.
- Chưa đọc cách `pps-education` triển khai Nghe ép.
