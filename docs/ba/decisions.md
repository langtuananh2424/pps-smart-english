# Quyết định và câu hỏi mở (BA)

Nguồn: trao đổi BA với chủ dự án ngày 2026-11-13 và 2026-11-14 (D-21..D-25). Ngày 2026-11-13 chủ dự án cho phép (BQ-14) và các quyết định D-01..D-20 đã được đưa vào PRD v0.2 / SRS v1.1; cột "Lệch PRD/SRS" và mục 5 giữ lại để truy vết lý do thay đổi. Câu hỏi BQ-xx còn mở vẫn chặn phần liên quan.

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
| D-07 | Ra mắt **một lượt** khi web xong đủ tính năng; **app Flutter làm song song**; chấp nhận web ra muộn hơn. Sau ra mắt là pilot rồi bảo trì. | SRS 1.2 (mobile giai đoạn 2) | `CLAUDE.md` đã sửa: mobile làm song song |
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
| D-21 | (2026-11-14, BQ-04) Ví tuần mặc định **20 lượt/tuần/khóa**, quản trị chỉnh được. Trọng số mục 3 vẫn chờ duyệt. | FR-AI-04 (số lượt chưa có) | `AiQuotaService`: ví theo khóa, mức nạp lấy từ cấu hình |
| D-22 | (2026-11-14, BQ-02/BQ-03; làm rõ 2026-11-14) Có hai nguồn lượt, như gói Pro của Claude: **(a) Lượt thường niên**: số lượt AI cấp khi đăng ký khóa, dùng được đến khi hết hạn khóa. **(b) Pool mua thêm** (như credits): không có hạn dùng, không reset, chỉ bị trừ khi lượt hàng tuần của khóa đã hết. **Đã làm rõ ở D-31:** lượt thường niên chính là lượt định kỳ. | FR-COM-05, FR-AI-04 | `user_ai_quota` phải tách thành ví tuần theo khóa, lượt thường niên theo khóa và pool riêng |
| D-23 | (2026-11-14, BQ-06) Kiểm tra đầu vào được làm lại; số lần làm lại cấu hình được; để trống = không cho làm lại. | FR-TEST-01 | Cấu hình số lần trên đề đầu vào |
| D-24 | (2026-11-14, BQ-08) Role gồm học sinh, nhân viên, giáo viên, giám đốc, quản trị viên, ... (danh sách mở). Có bảng chọn quyền và thêm role như `pps-education`. Quyền từng role: chưa chốt. | FR-ADM-04 (thay D-15) | Bảng `roles`, `permissions`, … (thay enum `User.Role`) |
| D-25 | (2026-11-14) **Ghi danh theo khóa** (course), không theo level. | SRS 6 (đã ghi theo khóa) | Đã đổi (baseline V5) |
| D-26 | (2026-11-14, đã duyệt) Dạng câu hỏi IELTS giữ theo chuẩn đề thi (Reading, Listening) như bộ luyện của IDP. Dạng PPS giữ 39 mục Figma, chỉ ở tầng hiển thị; kiểu chấm đi theo bảng ánh xạ, không theo số mục Figma. | FR-EX-04, FR-EX-05 | Tách kiểu chấm khỏi dạng hiển thị |
| D-27 | (2026-11-14, đã duyệt) Chấm nói không dùng PUT trực tiếp. Backend cấp presigned POST policy (khóa do server sinh, giới hạn dung lượng và kiểu audio, hết hạn ngắn), client tải thẳng lên MinIO, gọi `complete` để backend kiểm tra thật, rồi mới tạo job. Job dọn file không hoàn tất. | FR-AI-03 | Thêm endpoint xin quyền tải lên và xác nhận; job dọn dẹp |
| D-28 | (2026-11-14, đã duyệt) Cam gợi ý (FR-AI-06) và Cam hỏi đáp (FR-AI-07) dùng câu soạn sẵn do Phòng Đào tạo nhập. Lúc chạy không gọi AI, không trừ lượt. Câu ngoài danh sách: Cam nói chưa trả lời được. **Sửa FR-AI-07 trong SRS cần chủ dự án cho phép.** | FR-AI-06, FR-AI-07 | Bảng câu soạn sẵn; không cần AI cho Cam |
| D-29 | (2026-11-14, đã duyệt) Phát âm từng từ (FR-AI-05) đưa lên sớm vì là tính năng cốt lõi. Làm một đợt thử nghiệm ngắn trước để đo kích thước mô hình, độ trễ trên điện thoại tầm trung, độ chính xác và khả năng chạy trên web. **Đổi mức ưu tiên trong SRS cần chủ dự án cho phép.** | FR-AI-05 | Phần lớn ở Flutter và web, backend gần như không đổi |
| D-31 | (2026-11-14, BQ-15) Có hai loại lượt, không có ví "tổng cả khóa". (a) **Lượt định kỳ** của khóa: mặc định 20 lượt/tuần, chỉnh được theo tuần hoặc tháng, nạp lại đủ mức mỗi kỳ, không cộng dồn, dùng đến khi khóa hết hạn. (b) **Pool mua thêm**: mua như credits, không hết hạn, không reset, bán như sản phẩm riêng (FR-COM-05). Khi chấm AI: trừ (a), hết thì trừ (b), hết nữa thì báo hết lượt và mời mua. Việc mua thêm là quyết định của học viên, không do hệ thống tự động. | FR-AI-04, FR-COM-05 | `user_ai_quota` tách thành định kỳ theo khóa (chu kỳ tuần/tháng cấu hình được) và pool riêng |
| D-30 | (2026-11-14, BQ-07) Bài thi của khóa: mở khi học viên học xong **một tỷ lệ phần trăm của khóa**, chỉnh được, mặc định **80%**. Không cấp chứng nhận. Thi lại **N lần**, N chỉnh được, mặc định **0** (chỉ được một lần). | FR-EXAM-02 | Cấu hình ngưỡng mở và số lần thi lại; không có bảng chứng nhận |
| D-32 | (2026-11-14, BQ-04) **Trọng số trừ lượt AI**: bộ câu viết lại (CONNECTIVE_REWRITE, GRAMMAR_REWRITE) 1 lượt mỗi lần nộp, chỉ khi còn câu phải gọi AI; Writing Task 1: 2; Task 2: 3; Speaking: 2 mỗi part. Trọng số lưu thành bảng, quản trị chỉnh được. Job lỗi hoàn đúng số lượt đã trừ. | FR-AI-04 | `AiQuotaService` đang trừ phẳng 1 lượt |
| D-33 | (2026-11-14, BQ-02) Mua lại khi khóa còn hạn: **cộng dồn** thời hạn. "Không có hạn dùng" (D-22) chỉ nói về pool mua thêm. | FR-COM-06 | Không (`EntitlementGranter` đã cộng dồn) |
| D-34 | (2026-11-14, BQ-01/BQ-03 hoãn) Giá thật chưa chốt. Dữ liệu mẫu dùng **giá tạm**: gói 12 tháng = 1,6 × gói 6 tháng; gói lượt 10/20/50/100/200/500 = 49k/89k/199k/369k/699k/1.590k. Chỉ dùng để dựng giao diện, ghi rõ "giá mẫu". Trước khi chốt giá lượt AI phải đối chiếu chi phí AI thật. Mỗi khóa có hai sản phẩm 180 và 365 ngày. | FR-COM-01, FR-COM-05 | Seed demo; bỏ `COMBO` khỏi seed |
| D-35 | (2026-11-14, BQ-10) Học thử: mặc định mở **Topic đầu của Level đầu** mỗi khóa, Phòng Đào tạo đổi trong CMS. Học thử không có lượt AI, không được thi khóa (D-10). Thống nhất "1 Topic" (bỏ "1–2 Topic" trong `system-design.md`). | FR-COM-04, FR-EXAM-02 | Chặn bài thi với người chỉ học thử khi làm module thi |
| D-36 | (2026-11-14, BQ-11) Khóa hết hạn: **vẫn xem được lịch sử và kết quả**, không làm bài mới. | FR-COM-03 | Không (code đang như vậy) |
| D-37 | (2026-11-14, BQ-08) Ma trận quyền mặc định: Quản trị viên tất cả; Giám đốc `report.view`, `audit.view`, `order.view`; Kế toán `report.view`, `order.view`; Nhân viên hỗ trợ `student.view`, `order.view`, `activation.manage`; Biên tập viên `cms.content`, `regrade.manage`; Giáo viên `regrade.manage`, `student.view`. Thêm quyền `product.manage`, `order.view`, `student.view`. Bỏ role `STAFF` (gộp vào Nhân viên hỗ trợ). SRS chưa có chức năng riêng của Giáo viên trong B2C. | FR-ADM-04 | Migration seed quyền, bỏ `STAFF` |
| D-38 | (2026-11-14, BQ-12) MVP không tự phát hành hóa đơn. Xuất CSV đơn đã thanh toán đủ trường để Kế toán xuất hóa đơn điện tử bằng phần mềm của họ. Tích hợp nhà cung cấp hóa đơn sau, khi chốt cổng thanh toán. Nghĩa vụ hóa đơn với khách lẻ: Kế toán xác nhận. | FR-ADM-03 | Endpoint xuất CSV đơn hàng |
| D-39 | (2026-11-14, BQ-06) Kiểm tra đầu vào: Nghe, Đọc, Ngữ pháp, khoảng 40 câu, 30–40 phút; **không có Writing/Speaking** trong MVP. Khách làm được, phải đăng ký mới xem kết quả. Band → khóa gợi ý theo bảng 6 khóa mục 2 (bỏ bảng 4 khóa và combo ở Q-04). Đề do Phòng Đào tạo soạn. | FR-TEST-01, FR-GOAL-03 | Module mới |
| D-40 | (2026-11-14, BQ-05) "1.000 user" = **1.000 người dùng đồng thời**, như NFR-PERF-01. | NFR-PERF-01 | Kịch bản load test |
| D-41 | (2026-11-14, BQ-13) Tranh nguồn lực: phần học viên luyện tập ưu tiên **mobile**; quản trị và luồng mua ưu tiên **web**. | SRS 1.2 | Không (backend dùng chung API) |
| D-42 | (2026-11-14) `pps-speaking-engine` chạy **bản riêng** cho dự án, không dùng chung với `pps-education`. Đo RAM mỗi bản trong đợt thử phát âm (D-29) rồi chốt số bản. Máy thật 32 GB (SRS 2.3 ghi 8 GB là mức tối thiểu). | NFR-PERF-03 | Docker Compose thêm dịch vụ |
| D-43 | (2026-11-14) Ghi âm tối đa 3 phút mỗi part Speaking, 1 phút mỗi câu trả lời ngắn; file giữ 30 ngày. Cam hỏi đáp không giới hạn số câu mỗi ngày, chỉ giới hạn tần suất khoảng 30 câu/phút. | FR-AI-03, FR-AI-07 | Giới hạn trong luồng tải lên (D-27) |
| D-45 | (2026-11-14, **tạm hoãn**) "Chuyển đổi đề" = nhập đề có sẵn dạng Word/PDF thành câu hỏi có cấu trúc trong CMS. Chủ dự án định dùng CKEditor 5, nhưng nếu cần giấy phép thì tạm bỏ qua. CKEditor 5 dùng GPL (sản phẩm đóng mã cần bản thương mại) và tính năng nhập Word là gói trả phí, nên **hoãn cả CKEditor lẫn chuyển đổi đề**. Khi làm lại: cân nhắc thư viện MIT/BSD (ví dụ trình soạn TipTap/Lexical, đọc .docx bằng mammoth); backend lọc HTML theo danh sách thẻ cho phép (chống XSS). | FR-ADM-02 | Không (hoãn) |
| D-46 | (2026-11-14, bổ sung D-30) Tỷ lệ học xong khóa = số bài học đã hoàn thành ÷ số bài học đã xuất bản của khóa (bài xong khi xem đủ 80% video, quy tắc hiện có). Mỗi khóa có **một bộ đề thi** (bộ câu hỏi chế độ EXAM), chọn trong trang cài đặt khóa cùng ngưỡng % và số lần thi lại. | FR-EXAM-02 | Cột cấu hình trên `courses`; kiểm tra khi bắt đầu thi |
| D-47 | (2026-11-14, thiết kế theo D-31/D-32, chờ chủ dự án xem lại) Lượt định kỳ tính theo kỳ hiện tại (tuần từ 00:00 thứ Hai, tháng từ 00:00 ngày 1, giờ Việt Nam), không cần job nạp lại. Khi lượt định kỳ còn ít hơn trọng số tác vụ thì lấy phần còn lại từ pool mua thêm; tổng không đủ thì báo hết lượt. Tác vụ ngoài khóa đang còn hạn (ví dụ học thử) chỉ trừ pool. Hoàn lượt trả về đúng nguồn đã trừ. | FR-AI-04 | Ví theo khóa và kỳ; sổ cái ghi nguồn trừ |
| D-48 | (2026-11-14, Q-17) Chấm Speaking theo **4 tiêu chí IELTS**: Phát âm (điểm IPA/GOPT của `pps-speaking-engine` `/v1/analyze`); Độ trôi chảy (tính từ thời gian từng từ của `/v1/transcribe`: tốc độ nói, ngập ngừng, khoảng lặng); Từ vựng và Ngữ pháp (Haiku chấm trên bản chép lời theo bảng mô tả band Speaking công khai của IELTS). Band tổng = trung bình 4 tiêu chí, làm tròn 0.5. Trả bản chép lời, từ phát âm sai, nhận xét, gợi ý. Bản ghi im lặng/không rõ (quá ngắn, quá ít từ, độ tin cậy thấp — Whisper có thể bịa chữ) thì báo thu lại, không chấm, không trừ lượt. | FR-AI-03 | Job chấm nói, client engine, prompt rubric Speaking |
| D-49 | (2026-11-16) Chưa deploy server nào nên **gộp migration Flyway thành baseline sạch** (V1–V7 chung, V8 riêng PostgreSQL; đánh số lại ngày 2026-11-16 từ V100 và gộp cột Q&A D-55 vào baseline, vì chưa triển khai ở đâu ngoài local), bỏ các bước chuyển dữ liệu cũ; schema giống hệt chuỗi V1–V35 cũ (đã so `pg_dump`). Từ sau lần deploy production đầu tiên, mọi migration phá vỡ phải làm hai bước theo checklist. DB dev đã chạy chuỗi cũ phải tạo lại. Migration mới lấy số kế tiếp lớn nhất của cả hai thư mục (từ V9). | NFR (production-checklist P0 migration) | `db/migration`, `db/migration-pg` |
| D-50 | (2026-11-16) Lượt **kiểm tra đầu vào của khách bỏ dở** (chưa nộp, chưa gắn tài khoản) quá **3 ngày** kể từ lúc bắt đầu thì job tự xóa. | FR-TEST-01, D-39 | Job dọn `placement_attempts` |
| D-51 | (2026-11-16) **Việc hôm nay** trên Trang chủ suy ra từ lộ trình, không thêm bảng: tối đa 3 việc kế tiếp theo thứ tự lộ trình của khóa đang học (xem video bài chưa xong, làm bộ bài tập của bài đó, bài viết AI của topic nếu có); đếm việc đã xong trong ngày (giờ Việt Nam). | Trang chủ (Figma 03), US-307 | `GET /api/me/today` |
| D-52 | (2026-11-16) XP hiển thị cạnh Topic = **tổng XP học viên đã thực nhận** từ các bài và bộ bài của topic; không thêm quy tắc thưởng mới. | FR-RET-02 | Lộ trình theo user |
| D-53 | (2026-11-16) **Bong bóng Cam trên Trang chủ** chủ động nhắn theo quy tắc, không gọi AI, không trừ lượt: chào theo buổi; nhắc học khi hôm nay chưa học (giữ chuỗi); nhắc làm tiếp bài đang dở (kèm hạn nếu có giờ); nhắc khóa sắp hết hạn (mặc định 7 ngày, cấu hình được); báo thông báo chưa đọc. Chatbox hỏi đáp tự do vẫn chờ FR-SUP-01. | FR-AI-07, FR-RET-01, FR-SUP-01 | `GET /api/me/cam-messages` |
| D-54 | (2026-11-16) Seed demo và màn Khóa học dùng **6 khóa IELTS theo D-02**: Begin 0→3.0, Academic English 1 3.0→4.0, Academic English 2 4.0→5.0, Pre IELTS 5.0→5.5, IELTS Foundation 5.5→6.0, Super IELTS 6.0→7.0 (mốc '6.5/7.0' lấy 7.0). Giá 6 tháng theo D-02, 12 tháng = 1,6× (D-34). Chip lọc: Tất cả / Đang học / Học thử. Khóa TOEIC/Giao tiếp/Writing trong Figma là dữ liệu mẫu. | D-02, D-14, FR-LMS-01 | Seed `db/demo`, API catalog |
| D-55 | (2026-11-16) **Bộ Q&A gợi ý khóa** (4 bước, FR-GOAL-01): (1) Trình độ hiện tại tự đánh giá: Mất gốc / Cơ bản / Trung cấp / Khá; (2) Mục tiêu (tối đa 2): Thi chứng chỉ IELTS / Du học / Công việc / Học tốt ở trường / Chưa rõ; (3) Band mục tiêu 4.0–8.0 (bước 0.5); (4) Thời gian học mỗi tuần (<3h / 3–5h / 5–10h / >10h) và kỹ năng muốn cải thiện (Nghe/Nói/Đọc/Viết, nhiều lựa chọn). Khách lưu ở trình duyệt, ghi vào hồ sơ sau khi đăng ký; làm lại thì ghi đè. | FR-GOAL-01, US-201, US-101 | Màn Q&A, `PUT /api/me/qa` |
| D-56 | (2026-11-16) **Gợi ý khóa** dùng band hiện tại = band kiểm tra đầu vào mới nhất; chưa làm kiểm tra thì quy đổi trình độ tự đánh giá: Mất gốc 0, Cơ bản 3.0, Trung cấp 4.0, Khá 5.0 (cấu hình được). Khóa chính: `đầu vào ≤ band < đầu ra`; gợi ý thêm các khóa kế tiếp tới band mục tiêu (FR-GOAL-03), không combo. | FR-GOAL-03, US-203 | API gợi ý khóa |
| D-57 | (2026-11-16) **Học thử** (sửa theo D-35): mỗi khóa chỉ một topic học thử, mặc định Topic đầu của Level đầu; Phòng Đào tạo đổi topic trong CMS (bật cho topic này thì tắt topic khác). Nhãn hiển thị số bài thật của topic. | D-35, FR-LMS-02 | `FreeTrialAccessPolicy`, CMS topic, seed |
| D-58 | (2026-11-16) **Kỹ năng của topic**: topic có trường `skill` tùy chọn: LISTENING / READING / WRITING / SPEAKING / GRAMMAR_VOCAB. Phòng Đào tạo chọn trong CMS. Màn Bài giảng hiện icon theo kỹ năng và chip lọc theo các kỹ năng có trong khóa. | FR-LMS-01 | Migration V9, CMS topic, màn Bài giảng |
| D-59 | (2026-11-16) **Trạng thái bài giảng**: Chưa xem = chưa có tiến độ; Đang xem = đã xem một phần, chưa đạt 80%; Đã xem = đạt 80% (FR-LMS-03). "Xem tiếp" mở bài xem gần nhất chưa xong, phát từ vị trí đã dừng; không có bài như vậy thì mở bài chưa xong đầu tiên theo lộ trình. "Còn mm:ss" = thời lượng − vị trí đã dừng. | FR-LMS-03 | Roadmap trả vị trí đã dừng + lần xem gần nhất |
| D-60 | (2026-11-16) **Khóa hết hạn**: không xem lại video (giữ quyền hiện tại); khóa vẫn hiện trong danh sách, ở trạng thái khóa, có nút Gia hạn. | D-36 | Màn Bài giảng |
| D-61 | (2026-11-16) **Khóa học thử trong "khóa của tôi"**: trả thêm các khóa người dùng đã xem bài thử (có tiến độ ở topic học thử), gắn nhãn Học thử; topic khác khóa và có nút mua. | D-57, FR-AUTH-02 (PRD) | `GET /api/me/courses` |
| D-62 | (2026-11-16) **Thống kê bài tập theo khóa**: câu đã làm = số câu đã nộp trong các bộ luyện tập của khóa (mọi lần làm); độ chính xác = câu đúng / câu đã nộp; nhóm theo 10 dạng câu hiện có (từ nối, ngữ pháp, viết, nói), không theo dạng IELTS. "Nên luyện" = dạng có tỉ lệ đúng thấp nhất, cần ít nhất 10 câu đã nộp. Thẻ Cam gợi ý theo quy tắc, không gọi AI (như D-53). | FR-EX-04, FR-AI-06 | API thống kê bài tập, màn Bài tập |
| D-63 | (2026-11-16) **Band ước tính** ở màn Bài tập: band bài thi khóa gần nhất nếu đã thi, nếu chưa thì band kiểm tra đầu vào gần nhất; không có thì hiện "Chưa có" kèm nút làm kiểm tra đầu vào. Bài thi khóa hiện chỉ lưu điểm, chưa có bảng quy đổi band (FR-EXAM-03), nên tạm thời chỉ dùng band đầu vào. | FR-TEST-02, FR-EXAM-01 | API thống kê bài tập |
| D-64 | (2026-11-16) **Luyện thêm** (màn Figma 10, nút "Luyện 10 câu") để sau, theo issue #32; FR-EX-07 vẫn chờ chủ dự án chốt nguồn câu và cách trừ lượt. Màn Bài tập hiện banner Luyện thêm ở trạng thái "Sắp có". | FR-EX-07 | Màn Bài tập |
| D-65 | (2026-11-17) **Chấm bộ bài tập**: học viên làm hết rồi nộp cả bộ; màn kết quả hiện Đúng/Sai, đáp án và giải thích từng câu. "Thử lại kèm gợi ý khi sai lần đầu" (FR-EX-04) để sau, vì cần endpoint chấm từng câu. | FR-EX-03, FR-EX-04 | Màn làm bài |
| D-66 | (2026-11-17) **Viết và Nói trong bộ bài tập** để sau: đợt đầu màn làm bài hỗ trợ 8 dạng (từ nối, ngữ pháp, viết lại); câu WRITING/SPEAKING trong bộ hiện thẻ báo làm ở màn Viết/Nói và không tính điểm, làm ở issue riêng. | FR-EX-05, UC-10 | Màn làm bài |
| D-67 | (2026-11-17) **Tạm dừng** chỉ có ở bộ luyện tập có hẹn giờ; bộ không hẹn giờ không có nút tạm dừng, nháp tự lưu nên thoát ra vào lại vẫn làm tiếp. | FR-EX-06 | Màn làm bài |
| D-68 | (2026-11-17) **Làm tiếp lượt dở**: `GET /api/me/attempts` thêm lọc `bankId` và `open=true`; vào một bộ đang có lượt chưa nộp thì làm tiếp với nháp đã lưu, không có thì bắt đầu lượt mới. | FR-EX-06 | API lượt làm bài, màn làm bài |
| D-69 | (2026-11-17) **Tên yếu tố ngữ pháp** dùng thuật ngữ tiếng Anh chuẩn (Relative clause, Inversion, Passive voice...), lưu ở bảng `grammar_elements` cùng màu nhãn theo Figma; web đọc qua API, không ghi cứng. | FR-EX-01, D-44 | Bảng yếu tố ngữ pháp, màn làm bài |
| D-70 | (2026-11-17) **Câu hỏi có thêm câu hướng dẫn ngắn** (`instruction`, tùy chọn, ví dụ "Chọn từ nối thích hợp.") và dạng điền từ có thêm tên từng chỗ trống (tùy chọn). Không có thì web hiện tên dạng câu và "Chỗ trống n". | FR-EX-01 | CMS câu hỏi, màn làm bài |
| D-71 | (2026-11-17) **Màn kết quả** tạm chỉ hiện điểm, chưa có trạng thái Đạt/Chưa đạt; chờ chủ dự án cập nhật Figma. | FR-EX-03 | Màn làm bài |
| D-44 | (2026-11-14) **GRAMMAR_IDENTIFY** (nhận diện cấu trúc): học viên chọn cụm liền nhau trong câu và gắn một trong 8 yếu tố ngữ pháp. (a) Đề hiện số cụm cần tìm. (b) Cụm lồng nhau hiển thị bằng gạch chân nhiều lớp theo màu yếu tố, tối đa 3 lớp, kèm danh sách chip "cụm → nhãn". (c) **Chấm từng phần**: điểm câu = số cụm đúng ÷ max(số cụm đáp án, số cụm học viên chọn); câu "đúng" khi đạt 100%. (d) Không hỗ trợ cụm ngắt quãng trong MVP; người soạn đánh dấu phần lõi liền nhau. **GRAMMAR_BUILD** (xếp khối) vẫn chấm cả câu. Ứng với màn Figma nào: chủ dự án tự thiết kế. | FR-EX-02 | Điểm bài làm phải nhận số lẻ; trả số cụm cho học viên |

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
- **Pool mua thêm** (làm rõ 2026-11-14): như credits đã nạp. Thuộc tài khoản, không có hạn dùng, không reset; chỉ trừ khi lượt của khóa đang dùng trong tuần đã hết.
- **Lượt thường niên = lượt định kỳ** (chốt 2026-11-14, D-31): mỗi kỳ được nạp lại đủ mức, **mặc định 20 lượt/tuần**, chỉnh được theo tuần hoặc tháng. Lượt không dùng hết không cộng dồn sang kỳ sau. Được cấp từ lúc đăng ký khóa và nạp lại đến khi khóa hết hạn. Không có ví "tổng cả khóa" riêng.
- **Thứ tự trừ** (chốt 2026-11-14, D-31): (1) lượt định kỳ của khóa đang dùng → (2) pool mua thêm → (3) báo hết lượt chấm AI, mời mua thêm. Tác vụ không có lượt nào thì học viên quyết định có mua hay không.
- Trọng số (chốt 2026-11-14, D-32): viết lại câu 1; IELTS Writing Task 1: 2; Task 2: 3; mỗi phần Speaking: 2. Chỉnh được.
- Tác vụ AI ngoài khóa (bài kiểm tra đầu vào miễn phí, Cam trả lời hỗ trợ): không trừ lượt, chống lạm dụng bằng giới hạn tần suất (BQ-06, BQ-09).

## 4. Câu hỏi BA còn mở

| Mã | Câu hỏi | Chặn phần |
|---|---|---|
| BQ-01 | Giá gói 12 tháng của từng khóa? **Hoãn 2026-11-14; tạm dùng giá mẫu D-34.** | Giá thật trên trang mua |
| BQ-02 | **Đã trả lời 2026-11-14, xem D-33.** | — |
| BQ-03 | Giá từng mức gói lượt (10…500)? Hạn dùng: không có (D-22). **Giá: hoãn 2026-11-14; tạm dùng giá mẫu D-34.** | Giá thật `AI_ADDON` |
| BQ-15 | **Đã trả lời 2026-11-14, xem D-31.** Mức định kỳ chỉnh được theo từng khóa. | — |
| BQ-04 | **Đã trả lời 2026-11-14, xem D-21 và D-32.** | — |
| BQ-05 | **Đã trả lời 2026-11-14, xem D-40.** | — |
| BQ-06 | **Đã trả lời 2026-11-14, xem D-23 và D-39.** Nội dung đề: chờ Phòng Đào tạo. | Nội dung đề |
| BQ-07 | **Đã trả lời 2026-11-14, xem D-30.** | — |
| BQ-08 | **Đã trả lời 2026-11-14, xem D-24 và D-37.** | — |
| BQ-09 | **Hoãn 2026-11-14: bổ sung sau.** | Module hỗ trợ (chưa làm) |
| BQ-10 | **Đã trả lời 2026-11-14, xem D-35.** | — |
| BQ-11 | **Đã trả lời 2026-11-14, xem D-36.** | — |
| BQ-12 | **Đã trả lời 2026-11-14, xem D-38.** Nghĩa vụ hóa đơn: chờ Kế toán. | — |
| BQ-13 | **Đã trả lời 2026-11-14, xem D-41.** | — |
| ~~BQ-14~~ | ~~Cho phép sửa `CLAUDE.md` và cập nhật PRD/SRS theo các quyết định trên?~~ **Đã trả lời 2026-11-13: cho phép.** PRD v0.2, SRS v1.1, `CLAUDE.md` đã cập nhật. | — |

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

## 6. Sửa SRS theo D-27, D-28, D-29 (đã áp dụng 2026-11-14 với sự cho phép của chủ dự án)

- **FR-AI-03 (bổ sung):** Chấm nói dùng luồng tải lên qua presigned POST policy: xin quyền tải lên (kiểm tra người dùng, lượt làm bài, giới hạn tần suất và quota), client tải thẳng lên MinIO, gọi xác nhận để backend kiểm tra dung lượng và kiểu file rồi tạo job. File không được xác nhận trong thời hạn sẽ bị xóa tự động.
- **FR-AI-05 (đổi mức ưu tiên):** từ "Sau MVP" / Low thành MVP, sau đợt thử nghiệm kỹ thuật.
- **FR-AI-07 (viết lại):** Trợ lý Cam hỏi đáp học tập bằng câu hỏi soạn sẵn theo từng bài tập. Học viên chọn câu hỏi trong danh sách, Cam trả lời theo nội dung soạn sẵn. Không gọi AI, không trừ lượt. Câu hỏi ngoài danh sách: Cam thông báo chưa trả lời được.

## 7. Các câu hỏi còn mở sau lượt duyệt 2026-11-14

Các câu trước đây ở mục này đã có trả lời: BQ-07 (D-30), BQ-15 (D-31), giới hạn ghi âm và Cam (D-43). Còn mở: BQ-01, BQ-03 (giá thật), BQ-09, nội dung đề kiểm tra đầu vào, nghĩa vụ hóa đơn (Kế toán).
