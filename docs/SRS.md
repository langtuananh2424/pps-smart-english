# **ĐẶC TẢ YÊU CẦU PHẦN MỀM (SRS v1.1)**

# **DỰ ÁN: NỀN TẢNG BÁN KHÓA HỌC & HỆ THỐNG BÀI TẬP THÔNG MINH (B2C)**

Dựa trên Phương pháp 24 Nguyên tắc vàng khi học Tiếng Anh — Chuyên gia Nguyễn Hữu Phúc

**Lịch sử thay đổi**

| Phiên bản | Ngày | Nội dung |
| :---- | :---- | :---- |
| v1.0 | — | Bản đầu. |
| v1.1 | 2026-11-13 | Cập nhật theo quyết định D-01..D-20 trong `docs/ba/decisions.md` (chủ dự án cho phép, BQ-14): 6 khóa IELTS, kiểm tra đầu vào trong MVP, lượt AI theo tuần theo khóa, bài thi khóa, hỗ trợ học viên, phân quyền hybrid, app Flutter làm song song, MinIO, công nghệ đã chốt. Chi tiết luồng: `docs/ba/use-cases.md`. |

# **1\. GIỚI THIỆU (INTRODUCTION)**

## **1.1 Mục đích (Purpose)**

Tài liệu này đặc tả chi tiết toàn bộ các yêu cầu chức năng (Functional Requirements \- FR) và phi chức năng (Non-Functional Requirements \- NFR) cho sản phẩm ứng dụng bán lẻ khóa học tiếng Anh B2C độc lập. Tài liệu đóng vai trò là nguồn chân lý kỹ thuật (Single Source of Truth) phục vụ trực tiếp cho đội ngũ Kỹ thuật (Backend, Frontend, AI/ML), đội ngũ Kiểm thử (QA/QC), Thiết kế UI/UX và Ban Quản lý Dự án (PM/PO) trong suốt chu kỳ phát triển sản phẩm theo chuẩn IEEE 830 / ISO/IEC/IEEE 29148\.

## **1.2 Phạm vi sản phẩm (Product Scope)**

* **Tên sản phẩm:** PPS Smart English (B2C Retail Platform).  
* **Bối cảnh phân tách:** Hệ thống là một giải pháp B2C thương mại độc lập, tách rời hoàn toàn khỏi hệ thống LMS trường học liên kết hiện tại (`pps-education`), nhưng kế thừa tối đa nền tảng kiến trúc backend, công nghệ chấm AI và ngân hàng đề thi đã được chứng minh hiệu quả.  
* **Lộ trình giai đoạn 1 (MVP):** Tập trung số hóa 3 nguyên tắc đầu tiên:  
  * Nguyên tắc 1: Học từ kết nối qua video ngữ cảnh.  
  * Nguyên tắc 2: 8 Yếu tố ngữ pháp.  
  * Nguyên tắc 3: Viết phát triển ý có AI dẫn dắt và chấm chữa.  
  * Kiến trúc dữ liệu được thiết kế mở rộng sẵn sàng tiếp nhận 21 nguyên tắc còn lại trong các giai đoạn sau.  
* **Sản phẩm bán trong MVP:** 6 khóa IELTS online (Begin, Academic English 1, Academic English 2, Pre IELTS, IELTS Foundation, Super IELTS); mỗi khóa bán theo thời hạn 6 hoặc 12 tháng. Không bán bản offline, không bán combo. Danh mục và giá tham khảo: `docs/ba/decisions.md` mục 2.  
* **Nền tảng mục tiêu:** Web App Responsive (Desktop & Mobile Browser) và Mobile App Flutter (iOS & Android) phát triển song song, dùng chung API. Ra mắt một lượt khi web đủ tính năng; chấp nhận web ra muộn hơn để chờ đủ tính năng. Sau ra mắt: chạy pilot rồi bảo trì.

## **1.3 Thuật ngữ & Từ viết tắt (Definitions & Acronyms)**

| Thuật ngữ / Viết tắt | Định nghĩa chi tiết |
| :---- | :---- |
| **B2C** | Business-to-Consumer: Mô hình thương mại hướng trực tiếp đến người học đại chúng. |
| **SRS** | Software Requirements Specification: Tài liệu đặc tả yêu cầu phần mềm tiêu chuẩn. |
| **Scaffolding** | Phương pháp sư phạm "giàn giáo", phỏng vấn dẫn dắt từng bước giúp người học tự xây dựng bài viết hoàn chỉnh. |
| **Connective Words** | Các từ/cụm từ liên kết, collocations, cấu trúc liên từ tạo mạch văn tự nhiên. |
| **Freemium** | Mô hình cho phép trải nghiệm miễn phí giới hạn trước khi trả phí mua khóa. |
| **Lượt AI** | Đơn vị hạn mức dùng AI hiển thị cho học viên. Mỗi tác vụ AI trừ một số lượt theo trọng số. Token API chỉ ghi nội bộ để tính chi phí. |
| **Ví tuần** | Hạn mức lượt AI của một khóa đang còn hạn, nạp lại đủ mức mỗi 00:00 thứ Hai giờ Việt Nam, không cộng dồn. |
| **Pool lượt mua thêm** | Lượt AI học viên mua thêm, thuộc tài khoản, không reset theo tuần. |
| **Cam** | Trợ lý AI của sản phẩm: gợi ý khi luyện tập, trả lời câu hỏi học tập và câu hỏi hỗ trợ. |
| **Kiểm tra đầu vào** | Bài kiểm tra miễn phí xác định level (band) hiện tại của học viên. |
| **Q&A tư vấn** | Bộ câu hỏi xác định nhu cầu học (mục tiêu, thời gian, kỹ năng yếu); không dùng để xác định level. |
| **Ticket** | Yêu cầu hỗ trợ học viên gửi tới nhân viên, có trạng thái xử lý. |
| **VietQR** | Chuẩn thanh toán quét mã QR động chuyển khoản ngân hàng tự động đối soát tại Việt Nam. |

## **1.4 Tài liệu tham khảo (References)**

* Sách & Phương pháp luận "24 Nguyên Tắc Vàng Khi Học Tiếng Anh" — Tác giả Nguyễn Hữu Phúc.  
* Tài liệu PRD v0.2 — Nền tảng Bán Khóa Học & Hệ Thống Bài Tập Thông Minh PPS.  
* Bộ tài liệu BA `docs/ba/` (quyết định, use case, user story, lộ trình).  
* Mã nguồn và Kiến trúc hệ thống `pps-education` (Spring Boot 3, Flyway, PostgreSQL, Cloudflare R2).

# **2\. MÔ TẢ TỔNG QUAN HỆ THỐNG (OVERALL DESCRIPTION)**

## **2.1 Bối cảnh & Kiến trúc tổng thể (Product Perspective)**

Hệ thống vận hành theo kiến trúc Client-Server hiện đại, phi tập trung (decoupled architecture):

* **Backend Core:** Dịch vụ RESTful API viết bằng Java 21 \+ Spring Boot 3, cơ sở dữ liệu PostgreSQL 17 quản lý phiên bản bằng Flyway, Redis cho Pub/Sub kết quả AI, rate limit và cache.  
* **Frontend Clients:** Web Client (React 19 \+ Vite \+ TailwindCSS) và Mobile Client (Flutter 3.x), cả hai kết nối độc lập với Backend Core qua giao thức HTTPS/REST và WSS/SSE.  
* **Hạ tầng Media:** Video bài giảng và file âm thanh học viên nộp được lưu trên MinIO riêng của dự án (truy cập qua S3 API), kết hợp YouTube Private Embed.  
* **AI Engine:** Tích hợp LLM API (Anthropic) và Whisper STT thông qua hàng đợi bất đồng bộ (Asynchronous Queue Worker) để xử lý chấm điểm và hội thoại; chấm nói theo engine GOPT \+ wav2vec2 \+ Whisper như `pps-education`. Mọi nhà cung cấp gọi qua interface `AiGradingProvider`.  
* **Cổng thanh toán:** Kết nối qua interface `PaymentGateway`. Cổng thật chưa chốt (chờ Kế toán); trong lúc chờ dùng cổng giả lập và link kích hoạt.

## **2.2 Các tác nhân hệ thống (User Classes & Actors)**

* **Khách vãng lai (Guest):** Người dùng chưa đăng nhập; được duyệt danh mục khóa học, xem bài giảng mẫu, làm bài tập thử, làm Q&A tư vấn.  
* **Học viên Phổ thông (Free/Trial Student):** Đã đăng ký tài khoản; được làm kiểm tra đầu vào miễn phí và học thử Topic đầu của mỗi khóa (chi tiết học thử: BQ-10). Không được cấp lượt AI; lượt AI chỉ có khi mua khóa hoặc mua gói lượt.  
* **Học viên Trả phí (Paid/Premium Student):** Đã mua khóa học còn hạn; được mở khóa toàn bộ bài giảng, bài tập, bài thi của khóa và ví tuần lượt AI của khóa.  
* **Nhân sự nội bộ:** Mỗi người có một hoặc nhiều role; quyền thực tế theo bảng quyền (FR-ADM-04). Role mặc định gồm ít nhất: Admin, Giám đốc, Kế toán, Nhân viên hỗ trợ, Biên tập viên (Phòng Đào tạo), Giáo viên (việc của Giáo viên: BQ-08).  
* **Cam — trợ lý AI:** Tác nhân hệ thống gợi ý khi luyện tập, trả lời câu hỏi học tập và hỗ trợ.  
* **Hệ thống AI bên ngoài (External AI Services):** LLM Provider (chấm Writing, hội thoại Scaffolding, Cam), Whisper Provider và engine chấm nói.  
* **Hệ thống Cổng thanh toán (Payment Gateway):** Tiếp nhận thông tin đơn hàng và gửi webhook xác thực giao dịch thành công.

## **2.3 Môi trường vận hành (Operating Environment)**

* **Web Client:** Chạy trên các trình duyệt hiện đại (Chrome 110+, Safari 16+, Edge 110+, Firefox 110+) trên Desktop, Tablet và Mobile.  
* **Mobile Client:** Hỗ trợ hệ điều hành iOS 15.0+ và Android 10.0+ (API Level 29+).  
* **Hạ tầng Server:** Server riêng của công ty, Container Linux (Docker Compose), Cloudflare Tunnel \+ nginx, RAM tối thiểu 8GB, CPU 4 Cores.

# **3\. YÊU CẦU GIAO DIỆN BÊN NGOÀI (EXTERNAL INTERFACE REQUIREMENTS)**

## **3.1 Giao diện người dùng (User Interface \- UI/UX)**

* **Thiết kế nguồn:** Giao diện theo file Figma của dự án. Màu, bo góc, đổ bóng lấy từ token Figma (cấu hình trong `Web/tailwind.config.js`); phông Nunito (có tiếng Việt). Dữ liệu hiển thị trong Figma (tên khóa, đề, đáp án) là dữ liệu mẫu.  
* **Quy chuẩn bố cục:** Responsive mobile-first, vùng chạm tối thiểu 48x48px trên thiết bị di động.

## **3.2 Giao diện phần mềm & Giao thức (Software & Protocol Interfaces)**

* **Giao diện CSDL:** PostgreSQL JDBC Driver qua HikariCP connection pool.  
* **Giao diện Lưu trữ:** AWS S3 SDK tương thích MinIO Object Storage.  
* **Giao thức mạng:** 100% kết nối qua HTTPS/TLS 1.3; Server-Sent Events (SSE) cho phản hồi thời gian thực từ AI.

# **4\. ĐẶC TẢ YÊU CẦU CHỨC NĂNG (FUNCTIONAL REQUIREMENTS)**

## **PHÂN HỆ 1: XÁC THỰC & HỒ SƠ HỌC VIÊN (MODULE 1: AUTHENTICATION & PROFILES)**

| Mã Yêu Cầu | Tên Chức Năng | Mô Tả Nghiệp Vụ & Quy Tắc Thực Thi |
| :---- | :---- | :---- |
| **FR-AUTH-01** | Đăng ký tài khoản | MVP hỗ trợ đăng ký qua Google OAuth2 hoặc Email kèm mật khẩu (tối thiểu 8 ký tự, không bắt buộc chữ hoa, số hay ký tự đặc biệt). Apple Sign-in bắt buộc trước khi phát hành lên App Store (NFR-MOB-01). Đăng ký bằng số điện thoại chưa làm trong MVP. Tài khoản mới không được cấp lượt AI. |
| **FR-AUTH-02** | Đăng nhập & Quản lý phiên | Cấp phát JWT Token (Access Token hiệu lực 15 phút, Refresh Token hiệu lực 30 ngày lưu trong HttpOnly Cookie). Hỗ trợ đăng xuất và hủy token từ xa. |
| **FR-AUTH-03** | Quản lý Hồ sơ & Streak | Cho phép cập nhật thông tin cá nhân, theo dõi chuỗi ngày học liên tục (Streak), số XP tích lũy và điều chỉnh mục tiêu đầu ra linh hoạt theo từng khóa học. |

## **PHÂN HỆ 2: KHẢO SÁT MỤC TIÊU, KIỂM TRA ĐẦU VÀO & ĐIỀU HƯỚNG KHÓA HỌC (MODULE 2: GOAL, PLACEMENT & CATALOG NAVIGATION)**

| Mã Yêu Cầu | Tên Chức Năng | Mô Tả Nghiệp Vụ & Quy Tắc Thực Thi |
| :---- | :---- | :---- |
| **FR-GOAL-01** | Q&A tư vấn nhu cầu | Khách và học viên trả lời bộ câu hỏi tư vấn (bộ tạm, Phòng Đào tạo sửa được trong CMS) để xác định mục tiêu, band mong muốn, thời gian học mỗi tuần và kỹ năng muốn cải thiện. Q&A không dùng để xác định level. Khách chưa đăng nhập: câu trả lời lưu ở trình duyệt và ghi vào hồ sơ khi đăng ký. Làm lại thì ghi đè kết quả cũ. Khi chưa có kết quả kiểm tra đầu vào, học viên có thể tự chọn trình độ ước lượng. |
| **FR-GOAL-02** | Hệ quy chiếu mục tiêu động | Lưu trữ mục tiêu theo cặp `(target_type, target_value)` linh hoạt. MVP chỉ dùng mục tiêu IELTS (band); cấu trúc dữ liệu giữ khả năng mở rộng sang Phổ thông, Cambridge, Giao tiếp ở giai đoạn sau. |
| **FR-GOAL-03** | Đề xuất khóa học | Gợi ý khóa đơn lẻ dựa trên level (FR-TEST-02) và nhu cầu (FR-GOAL-01): chọn khóa có đầu vào ≤ band hiện tại < đầu ra; nếu band mục tiêu cao hơn đầu ra thì gợi ý thêm các khóa kế tiếp theo thứ tự. Chưa có kết quả kiểm tra đầu vào thì gợi ý theo trình độ tự chọn kèm lời mời làm kiểm tra. Bảng ánh xạ band → khóa lấy từ dải đầu vào/đầu ra của khóa, Phòng Đào tạo sửa được. Không gợi ý combo. |
| **FR-TEST-01** | Kiểm tra đầu vào | Học viên làm miễn phí bài kiểm tra đầu vào do Phòng Đào tạo soạn (FR-ADM-06), giao diện mô phỏng thi, đếm ngược theo giờ máy chủ, không được tạm dừng; hết giờ thì máy chủ tự nộp. Mất mạng: câu trả lời lưu tạm trên máy và gửi lại khi có mạng. Phần Writing/Speaking (nếu có) chấm AI, không trừ lượt, giới hạn tần suất. Cấu trúc đề, số lần làm lại, quyền của khách chưa đăng nhập: BQ-06. |
| **FR-TEST-02** | Kết quả kiểm tra đầu vào | Hiển thị band ước tính từng kỹ năng và level tổng; lưu vào hồ sơ để dùng cho FR-GOAL-03. Phần chấm AI bị lỗi: hiển thị "đang chấm", chấm lại tự động, level tạm tính từ phần đã chấm. |

## **PHÂN HỆ 3: QUẢN LÝ & TRÌNH PHÁT BÀI GIẢNG VIDEO TỪ KẾT NỐI (MODULE 3: COURSE & VIDEO LMS)**

| Mã Yêu Cầu | Tên Chức Năng | Mô Tả Nghiệp Vụ & Quy Tắc Thực Thi |
| :---- | :---- | :---- |
| **FR-LMS-01** | Cấu trúc phân cấp nội dung | Tổ chức khóa học theo mô hình cây: `Course` \-\> `Level` \-\> `Topic` \-\> `Lesson` (Video Từ kết nối / Video Phản xạ). |
| **FR-LMS-02** | Trình phát Video tương tác | Streaming video độ trễ thấp từ MinIO / YouTube Private; phụ đề tương tác song ngữ chạy đồng bộ. Chạm vào từ kết nối trong phụ đề sẽ dừng 3s và mở popup giải nghĩa \+ phát âm. |
| **FR-LMS-03** | Theo dõi tiến độ xem video | Tự động ghi nhận thời lượng xem; đánh dấu bài học hoàn thành khi học viên theo dõi đạt từ 80% thời lượng trở lên. |

## **PHÂN HỆ 4: NGÂN HÀNG CÂU HỎI & ĐỘNG CƠ BÀI TẬP TƯƠNG TÁC (MODULE 4: MULTI-TYPE EXERCISE ENGINE)**

| Mã Yêu Cầu | Tên Chức Năng | Mô Tả Nghiệp Vụ & Quy Tắc Thực Thi |
| :---- | :---- | :---- |
| **FR-EX-01** | Bài tập Học từ kết nối | Bao gồm: Trắc nghiệm logic ngữ cảnh (Cause, Effect, Contrast, Addition); Ghép nối 2 vế câu bằng liên từ; Điền từ nối vào chỗ trống trong đoạn văn ngữ cảnh. |
| **FR-EX-02** | Bài tập 8 Yếu tố ngữ pháp | 8 yếu tố là các cấu trúc áp dụng vào bài viết IELTS: Mệnh đề quan hệ tính ngữ; Câu đảo ngữ; Câu bị động; Chủ ngữ giả; Mệnh đề phân từ; Mệnh đề danh từ; Sử dụng "seem, be likely, tend to"; Các cấu trúc quen thuộc khác. Bài tập bao gồm: Nhận diện cấu trúc trong câu mẫu; Tìm và sửa lỗi sai (Spot & Fix); Xếp khối tạo câu đúng cấu trúc; Viết lại câu bằng cấu trúc yêu cầu. |
| **FR-EX-03** | Chấm điểm & Giải thích tức thì | Tự động chấm điểm bài tập cấu trúc tức thì (\< 100ms) kèm lời giải thích ngữ pháp chi tiết cho các phương án đúng và sai. |
| **FR-EX-04** | Dạng bài Luyện tập PPS | Hỗ trợ 39 dạng bài "Luyện tập PPS" theo Figma (danh sách và cách chấm: `docs/ba/use-cases.md` mục 3.1), phần lớn kế thừa `pps-education`. Dữ liệu lưu theo kiểu chấm; giao diện theo dạng hiển thị. Dạng tự chấm trả kết quả ngay (FR-EX-03); dạng viết/nói chấm AI qua hàng đợi và trừ lượt (FR-AI-04). Một số dạng cho phép thử lại kèm gợi ý khi sai lần đầu. |
| **FR-EX-05** | Dạng bài IELTS 4 kỹ năng | Reading, Listening, Writing (Task 1, Task 2), Speaking (Part 1–3) với giao diện giống thi trên máy: đề bên trái, câu hỏi bên phải, thanh điều hướng câu, đánh dấu câu xem lại. Mỗi bài có 2 chế độ: Luyện tập (được tạm dừng, có Cam gợi ý, FR-AI-06) và Mô phỏng thi (FR-EXAM-01). Kết quả Reading/Listening gồm số câu đúng, band (FR-EXAM-03) và giải thích từng câu. |
| **FR-EX-06** | Thời gian làm bài, tạm dừng | Thời gian làm bài cấu hình theo từng đề / bộ bài tập. Chế độ Luyện tập: được tạm dừng, lưu câu trả lời và thời gian còn lại, mở lại thì làm tiếp. Chế độ Kiểm tra / Thi: không tạm dừng. Hết giờ thì máy chủ tự nộp. |
| **FR-EX-07** | Luyện thêm | Học viên chọn dạng bài và luyện các câu ngoài lộ trình Topic. Nguồn câu hỏi và cách trừ lượt AI: chờ chủ dự án quyết định. |

## **PHÂN HỆ 5: TRỢ LÝ AI VIẾT PHÁT TRIỂN Ý & CHẤM CHỮA BÀI (MODULE 5: AI WRITING & EVALUATION ENGINE)**

| Mã Yêu Cầu | Tên Chức Năng | Mô Tả Nghiệp Vụ & Quy Tắc Thực Thi |
| :---- | :---- | :---- |
| **FR-AI-01** | Dẫn dắt phát triển ý (Scaffolding) | Bước 1: Hiển thị câu hỏi dẫn dắt (Root Question). Bước 2: AI hỏi sâu 2–3 câu hỏi tiếp nối dựa trên câu trả lời của học viên. Bước 3: AI tự động tổng hợp câu trả lời thành bản nháp hoàn chỉnh có từ kết nối. |
| **FR-AI-02** | Chấm bài tự luận AI chuyên sâu | Kế thừa rubric PPS: Bôi đỏ lỗi sai trên bài gốc (`markedAnswer`), đưa ra bản sửa chuẩn (`correctedAnswer`) bảo toàn ý tưởng gốc, phân tích tần suất/độ chính xác của từ kết nối, đánh giá các yếu tố trong 8 Yếu tố ngữ pháp đã dùng đúng hoặc sai kèm gợi ý nâng cấp câu, cảnh báo lạc đề (`gateNote`). |
| **FR-AI-03** | Chấm phát âm & Phản xạ nói | Thu âm câu trả lời qua mic; sử dụng Whisper STT chuyển thành văn bản; chấm điểm độ chính xác phát âm, độ trôi chảy và thời gian phản xạ theo rubric. |
| **FR-AI-04** | Lượt AI theo tuần, theo khóa | Mỗi khóa còn hạn có một ví tuần lượt AI, nạp lại đủ mức mỗi 00:00 thứ Hai (Asia/Ho_Chi_Minh), lượt thừa không chuyển sang tuần sau; khóa hết hạn thì ví dừng. Tác vụ AI phát sinh trong khóa nào thì trừ ví tuần của khóa đó theo trọng số tác vụ. Thứ tự trừ: ví tuần của khóa → pool lượt mua thêm (FR-COM-05) → từ chối, báo hết lượt và mời mua thêm. Trước khi gọi AI hệ thống giữ chỗ lượt; xong thì trừ thật; AI lỗi thì hoàn lượt. Mọi cộng/trừ ghi sổ cái; token tiêu hao ghi vào `ai_grading_token_usage` để kiểm soát chi phí. Tác vụ AI ngoài khóa (kiểm tra đầu vào, Cam hỗ trợ) không trừ lượt, chỉ giới hạn tần suất. Số lượt mỗi tuần của từng khóa và trọng số: BQ-04. |
| **FR-AI-05** | Chấm phát âm từng từ | Học viên đọc một từ trong popup giải nghĩa (FR-LMS-02). Mô hình nhận dạng âm vị chạy ngay trên thiết bị, không gửi âm thanh lên máy chủ; tính điểm GOP theo từng âm so với chuỗi âm chuẩn của từ, trả điểm cả từ, điểm từng âm và âm bị đọc nhầm. |
| **FR-AI-06** | Cam gợi ý khi luyện tập | Ở chế độ Luyện tập IELTS, Cam hiện bong bóng gợi ý theo dạng câu; gợi ý dài chuyển thành popup; học viên thu gọn được Cam. Gợi ý ưu tiên soạn sẵn theo câu; chỉ gọi AI khi chủ dự án duyệt. Không có ở chế độ Mô phỏng thi và kiểm tra đầu vào. |
| **FR-AI-07** | Trợ lý AI (Cam) hỏi đáp học tập | Học viên hỏi Cam các câu hỏi về bài học và tiếng Anh theo thiết kế Figma mục "Trợ lý AI". Cách tính lượt: chờ chủ dự án quyết định. |

## **PHÂN HỆ 6: THƯƠNG MẠI & QUẢN LÝ GÓI HỌC (MODULE 6: E-COMMERCE & PAYMENTS)**

| Mã Yêu Cầu | Tên Chức Năng | Mô Tả Nghiệp Vụ & Quy Tắc Thực Thi |
| :---- | :---- | :---- |
| **FR-COM-01** | Quản lý Giỏ hàng & Đơn hàng | Hỗ trợ mua từng khóa học theo thời hạn 6 hoặc 12 tháng, hoặc mua gói lượt AI (FR-COM-05). Không bán combo. Không hoàn tiền. Giá gói 12 tháng: BQ-01. |
| **FR-COM-02** | Thanh toán trực tuyến | Tạo đơn hàng và thanh toán qua interface `PaymentGateway`; webhook đối soát tự động kích hoạt trong vòng 3 giây không cần can thiệp thủ công. Cổng thật (VietQR / PayOS / VNPay / MoMo) chưa chốt, chờ Kế toán, làm cuối; trong lúc chờ dùng cổng giả lập. |
| **FR-COM-03** | Phân quyền & Kích hoạt bản quyền | Cập nhật trạng thái `PAID` trên hệ thống, mở khóa toàn bộ quyền truy cập nội dung bài giảng, bài tập, bài thi của khóa đã mua và tạo ví tuần lượt AI của khóa. |
| **FR-COM-04** | Kích hoạt bằng link | Admin tạo, xem và thu hồi link kích hoạt gắn với một sản phẩm, có số lượt dùng tối đa và ngày hết hạn. Người dùng đã đăng nhập, đã xác thực email, kích hoạt một lần cho mỗi link và nhận đúng thời hạn, lượt AI của sản phẩm mà không cần thanh toán. |
| **FR-COM-05** | Gói lượt AI mua thêm | Học viên mua gói 10, 20, 50, 100, 200 hoặc 500 lượt; lượt cộng vào pool của tài khoản, không reset theo tuần, ghi sổ cái. Giá từng mức và hạn dùng của pool: BQ-03. |
| **FR-COM-06** | Gia hạn khóa | Học viên mua lại khóa đang còn hạn để gia hạn. Cách tính thời hạn (cộng dồn hay tính lại từ ngày mua): BQ-02. |
| **FR-COM-07** | Quản lý sản phẩm và giá | Người có quyền tạo/sửa khóa bán (tên, khóa liên kết, thời hạn, giá, số lượt/tuần), gói lượt (số lượt, giá) và bật/tắt bán. Đổi giá không ảnh hưởng đơn đã tạo. Mọi thay đổi ghi nhật ký (FR-ADM-05). |

## **PHÂN HỆ 7: GIỮ CHÂN & GAMIFICATION (MODULE 7: RETENTION & GAMIFICATION)**

| Mã Yêu Cầu | Tên Chức Năng | Mô Tả Nghiệp Vụ & Quy Tắc Thực Thi |
| :---- | :---- | :---- |
| **FR-RET-01** | Chuỗi ngày học liên tục (Streak) | Đếm số ngày học viên hoàn thành ít nhất 1 bài học/bài tập mỗi ngày; hiển thị huy hiệu ngọn lửa Streak tại vị trí nổi bật trên thanh điều hướng. |
| **FR-RET-02** | Điểm kinh nghiệm (XP) & Danh hiệu | Thưởng XP sau mỗi bài học hoàn thành; tích lũy XP để thăng hạng danh hiệu học tập (Tập sự, Tự tin, Chuyên gia). |
| **FR-RET-03** | Thông báo đẩy thông minh | Gửi thông báo đẩy qua Web Push (VAPID) / Mobile Push (FCM) nhắc nhở lúc 20:00 nếu chưa duy trì chuỗi Streak, thông báo khi bài viết đã được AI chấm xong, hoặc khi nhân viên trả lời ticket. |

## **PHÂN HỆ 8: CỔNG QUẢN TRỊ NỘI DUNG CMS (MODULE 8: ADMIN CMS PORTAL)**

| Mã Yêu Cầu | Tên Chức Năng | Mô Tả Nghiệp Vụ & Quy Tắc Thực Thi |
| :---- | :---- | :---- |
| **FR-ADM-01** | Quản lý Khóa học & Bài giảng | Cho phép tạo Level, Topic, upload video trực tiếp lên MinIO hoặc dán mã nhúng YouTube, tải lên và đồng bộ file phụ đề VTT/SRT. |
| **FR-ADM-02** | Quản lý & Import ngân hàng câu hỏi | Kế thừa công cụ import hàng loạt từ file Excel/Word của `pps-education`, hỗ trợ cấu hình gắn thẻ yếu tố ngữ pháp và thiết lập đáp án tự động. Soạn và xem trước câu hỏi cho đủ các dạng FR-EX-04, FR-EX-05. |
| **FR-ADM-03** | Báo cáo & Thống kê Doanh thu | Dashboard thời gian thực theo dõi số lượng người dùng mới, tỷ lệ chuyển đổi Freemium sang Paid, doanh thu theo gói học và chi phí tiêu thụ token AI. Báo cáo cho Kế toán: BQ-12. |
| **FR-ADM-04** | Phân quyền hybrid | Quyền là đơn vị nhỏ nhất, mã dạng `module.action`, nhóm theo module, mỗi quyền có một câu mô tả tiếng Việt. Role là tập quyền, lưu trong DB, tạo thêm được. Nhân sự có nhiều role và có thể được cấp thêm (GRANT) hoặc chặn (REVOKE) từng quyền riêng, có ngày hết hạn tùy chọn. Quyền hiệu lực = hợp quyền các role − REVOKE còn hạn + GRANT còn hạn; thay đổi có hiệu lực ở lần gọi API kế tiếp. Không được xóa quyền quản trị cuối cùng. Mô hình như `pps-education` nhưng gọn hơn (khoảng 30–40 quyền). Ma trận role mặc định: `docs/ba/use-cases.md` mục 7.1 (chờ BQ-08). |
| **FR-ADM-05** | Nhật ký thao tác nội bộ | Ghi ai, lúc nào, thao tác, đối tượng, giá trị trước/sau và lý do cho mọi thay đổi của nhân sự nội bộ. Không sửa, không xóa được. Lọc theo người, đối tượng, ngày. |
| **FR-ADM-06** | Quản lý đề thi, đề đầu vào, bảng quy đổi | Biên tập viên tạo đề (loại: đầu vào / thi khóa / luyện tập), chọn kỹ năng, phần, dạng câu, nhập câu hỏi, audio, bài đọc, hình, cấu hình thời gian từng phần và chế độ tạm dừng, nhập bảng quy đổi band, xem trước rồi xuất bản. Đề đã có người làm thì sửa đáp án phải chấm lại; không xóa đề, chỉ ngừng xuất bản. |

## **PHÂN HỆ 9: THI (MODULE 9: EXAMS)**

| Mã Yêu Cầu | Tên Chức Năng | Mô Tả Nghiệp Vụ & Quy Tắc Thực Thi |
| :---- | :---- | :---- |
| **FR-EXAM-01** | Mô phỏng thi IELTS trên máy | Giao diện thi trên máy cho 4 kỹ năng; đồng hồ theo giờ máy chủ, không tạm dừng, không có gợi ý Cam; xem lại trước khi nộp, xác nhận nộp; hết giờ máy chủ tự nộp. Đáp án chỉ trả sau khi nộp. |
| **FR-EXAM-02** | Bài thi của khóa | Mỗi khóa có bài thi riêng do Phòng Đào tạo soạn (không dùng đề sách có bản quyền); chỉ học viên đã mua khóa còn hạn mới được thi, học thử không được thi. Writing/Speaking chấm AI và trừ ví tuần của khóa. Kết quả: band từng kỹ năng, band tổng, xem lại đáp án. Số lần thi, điều kiện mở bài thi, chứng nhận: BQ-07. |
| **FR-EXAM-03** | Quy đổi band | Reading/Listening: số câu đúng đổi sang band theo bảng do Phòng Đào tạo nhập (FR-ADM-06). Writing/Speaking: AI trả band theo 4 tiêu chí. Band tổng = trung bình 4 kỹ năng, làm tròn theo quy tắc IELTS (lẻ 0,25 lên 0,5; lẻ 0,75 lên số nguyên kế tiếp). |

## **PHÂN HỆ 10: HỖ TRỢ HỌC VIÊN (MODULE 10: STUDENT SUPPORT)**

| Mã Yêu Cầu | Tên Chức Năng | Mô Tả Nghiệp Vụ & Quy Tắc Thực Thi |
| :---- | :---- | :---- |
| **FR-SUP-01** | Chatbox Cam trả lời hỗ trợ | Học viên hỏi trong chatbox của app/web; Cam trả lời câu đơn giản dựa trên FAQ nội bộ và dữ liệu tài khoản của chính học viên (khóa, hạn, đơn gần nhất, lượt còn lại). Câu hỏi về tiền, lỗi thanh toán hoặc Cam không chắc thì Cam đề nghị gặp nhân viên. Cam không được hứa hoàn tiền, cộng lượt, đổi khóa. Không trừ lượt AI, giới hạn tần suất riêng. Kênh ngoài app: Zalo, hotline. Nguồn FAQ và quyền của khách: BQ-09. |
| **FR-SUP-02** | Ticket và chat với nhân viên | Học viên bấm "Gặp nhân viên" thì hệ thống tạo ticket kèm lịch sử chat với Cam; nhân viên nhận xử lý và chat với học viên trong cùng chatbox theo thời gian thực; học viên nhận thông báo đẩy khi có trả lời. Ngoài giờ làm việc: báo thời gian phản hồi dự kiến và gợi ý Zalo/hotline. Trạng thái, phân công, giờ làm việc, cam kết phản hồi: BQ-09. |
| **FR-SUP-03** | Công cụ nhân viên hỗ trợ | Nhân viên tra cứu học viên (email, tên, mã đơn); xem khóa, hạn, đơn, lượt, ticket; kích hoạt khóa thủ công khi lỗi thanh toán; cộng lượt bù; mở khóa tài khoản; gửi lại email xác thực. Mỗi thao tác thay đổi dữ liệu bắt buộc nhập lý do và ghi nhật ký (FR-ADM-05). Không xem doanh thu tổng. |

# **5\. ĐẶC TẢ YÊU CẦU PHI CHỨC NĂNG (NON-FUNCTIONAL REQUIREMENTS \- NFR)**

## **5.1 Hiệu năng & Khả năng đáp ứng (NFR-PERFORMANCE)**

* **NFR-PERF-01:** Thời gian phản hồi API trung bình cho các tác vụ tra cứu dữ liệu và nộp bài trắc nghiệm \$\\le 200\\text{ms}\$ ở điều kiện tải 1.000 người dùng đồng thời (CCU). Chủ dự án kiểm chứng bằng load test trước ra mắt; nghĩa của "1.000 người dùng": BQ-05.  
* **NFR-PERF-02:** Thời gian khởi chạy phát video (buffer latency) \$\\le 1.0\\text{s}\$.  
* **NFR-PERF-03:** Thời gian AI trả về kết quả chấm bài viết tự luận \$\\le 3\\text{–}5\\text{s}\$; trong thời gian chờ, giao diện bắt buộc hiển thị animation tiến độ trực quan.

## **5.2 Bảo mật & Toàn vẹn dữ liệu (NFR-SECURITY)**

* **NFR-SEC-01:** Toàn bộ mật khẩu người dùng phải được băm bằng thuật toán `BCrypt` với độ phức tạp salt rounds \$\\ge 12\$.  
* **NFR-SEC-02:** Bảo vệ bản quyền video: Video lưu trên MinIO chỉ được phân phối qua Pre-signed URL có thời hạn hiệu lực tối đa 120 phút; chặn tải trực tiếp và chặn nhúng ngoài domain hệ thống.  
* **NFR-SEC-03:** Áp dụng cơ chế Rate Limiting trên các endpoint AI (tối đa 5 request/phút/user) nhằm ngăn chặn hành vi spam và khai thác tài nguyên.  
* **NFR-SEC-04:** Tuân thủ tiêu chuẩn an toàn OWASP Top 10 (chống SQL Injection, XSS, CSRF, Broken Access Control).

## **5.3 Độ sẵn sàng & Tin cậy (NFR-RELIABILITY & AVAILABILITY)**

* **NFR-AVAIL-01:** Độ sẵn sàng hoạt động của hệ thống đạt tối thiểu 99.5% Uptime trong tháng, ngoại trừ thời gian bảo trì định kỳ có thông báo trước.  
* **NFR-AVAIL-02:** Sao lưu dữ liệu CSDL tự động hàng ngày (Automated Daily Database Backup) và lưu trữ phân tán, cam kết RPO \$\\le 24\\text{h}\$ và RTO \$\\le 2\\text{h}\$.

## **5.4 Khả năng mở rộng (NFR-SCALABILITY)**

* **NFR-SCAL-01:** Backend Spring Boot được thiết kế hoàn toàn Stateless, hỗ trợ mở rộng ngang (Horizontal Auto-scaling) khi lượng truy cập tăng đột biến.  
* **NFR-SCAL-02:** Cấu trúc bảng và danh mục dữ liệu được thiết kế động, sẵn sàng mở rộng tiếp nhận trọn vẹn 24 Nguyên tắc vàng mà không làm thay đổi cấu trúc bảng cốt lõi.

## **5.5 Tính tiện dụng & Khả năng tiếp cận (NFR-USABILITY)**

* **NFR-USAB-01:** Giao diện đáp ứng chuẩn thiết kế Responsive 100% trên các kích thước màn hình từ 375px đến 1920px+.  
* **NFR-USAB-02:** Màu sắc và độ tương phản đạt chuẩn WCAG 2.1 AA, đảm bảo trải nghiệm học tập không gây mỏi mắt trong các phiên học kéo dài từ 30–45 phút.

## **5.6 Ứng dụng di động (NFR-MOBILE)**

* **NFR-MOB-01:** App Flutter (iOS, Android) dùng chung REST API với web, đủ tính năng phía học viên như web, không có màn quản trị. Trước khi phát hành lên App Store bắt buộc có Apple Sign-in và lưu refresh token trong secure storage. Thông báo đẩy qua FCM.

# **6\. SƠ ĐỒ THỰC THỂ DỮ LIỆU CỐT LÕI (CONCEPTUAL DATA MODEL)**

Hệ thống kế thừa cấu trúc dữ liệu đã được kiểm chứng từ `pps-education` và bổ sung các thực thể phục vụ kinh doanh B2C:

* `users` & `user_profiles`: Thông tin tài khoản người dùng, mục tiêu học tập (`target_type`, `target_value`), điểm kinh nghiệm XP, chuỗi ngày học Streak.  
* `roles`, `permissions`, `role_permissions`, `user_roles`, `user_permission_overrides`: Phân quyền hybrid cho nhân sự nội bộ (FR-ADM-04).  
* `audit_logs`: Nhật ký thao tác nội bộ (FR-ADM-05).  
* `principles`: Danh mục 24 Nguyên tắc vàng (`code`: `PRIN_01` đến `PRIN_24`, `name`, `status`: ACTIVE/PLANNED).  
* `courses`, `course_levels`, `topics`, `lessons`: Cây cấu trúc tổ chức khóa học, chuyên đề và nội dung video bài giảng.  
* `question_banks`, `questions`, `question_choices`: Ngân hàng câu hỏi đa dạng loại hình (FR-EX-01..05), có cấu hình thời gian và chế độ làm bài (FR-EX-06).  
* `exams`, `band_conversion_tables`: Đề kiểm tra đầu vào, đề thi khóa và bảng quy đổi band (FR-ADM-06, FR-EXAM-03).  
* `placement_attempts`, `exam_attempts`: Lượt làm kiểm tra đầu vào và bài thi khóa.  
* `products`: Sản phẩm bán (khóa theo thời hạn, gói lượt AI) và giá (FR-COM-07).  
* `orders`, `order_items`, `payment_transactions`: Quản lý giỏ hàng, thông tin đơn hàng, giao dịch thanh toán và nhật ký webhook đối soát.  
* `user_course_enrollments`: Quản lý trạng thái, thời hạn và quyền truy cập nội dung của từng học viên.  
* `course_ai_wallets`, `ai_credit_pool`, `ai_quota_ledger`: Ví tuần theo khóa, pool lượt mua thêm và sổ cái mọi lần cộng/trừ lượt (FR-AI-04, FR-COM-05).  
* `student_exercise_attempts`, `student_answers`, `student_answer_grading`: Lịch sử làm bài (kèm trạng thái tạm dừng, thời gian còn lại), chi tiết câu trả lời và kết quả đánh giá chấm điểm.  
* `support_tickets`, `support_messages`: Ticket và tin nhắn hỗ trợ (FR-SUP-01..03).  
* `ai_grading_token_usage`: Nhật ký theo dõi mức độ tiêu thụ token của từng lượt gọi AI.

Tên bảng mới ở trên là tên khái niệm; tên vật lý chốt khi thiết kế chi tiết (`docs/system-design.md`).

# **7\. MA TRẬN TRUY VẾT YÊU CẦU (TRACEABILITY MATRIX)**

| Mã Phân Hệ | Yêu Cầu PRD Tương Ứng | Mã FR / NFR Trong SRS | Mức Độ Ưu Tiên | Giai Đoạn Triển Khai |
| :---- | :---- | :---- | :---- | :---- |
| Phân hệ 1 | Quản lý Tài khoản & Quyền Freemium | FR-AUTH-01 đến 03 | High | MVP |
| Phân hệ 2 | Q&A tư vấn, Kiểm tra đầu vào & Điều hướng | FR-GOAL-01 đến 03, FR-TEST-01 đến 02 | High | MVP |
| Phân hệ 3 | Bài giảng Video Từ kết nối | FR-LMS-01 đến 03 | High | MVP |
| Phân hệ 4 | Bài tập Luyện tập PPS, IELTS, thời gian làm bài | FR-EX-01 đến 06 | High | MVP |
| Phân hệ 4 | Luyện thêm | FR-EX-07 | Medium | MVP |
| Phân hệ 5 | AI Viết phát triển ý, Chấm chữa, Lượt AI theo khóa, Cam gợi ý | FR-AI-01 đến 04, FR-AI-06 | High | MVP |
| Phân hệ 5 | Trợ lý AI hỏi đáp | FR-AI-07 | Medium | MVP nếu kịp |
| Phân hệ 5 | Phát âm từng từ trên thiết bị | FR-AI-05 | Low | Sau MVP |
| Phân hệ 6 | Đơn hàng, Gói lượt, Gia hạn, Sản phẩm | FR-COM-01, 03 đến 07 | High | MVP |
| Phân hệ 6 | Cổng thanh toán thật | FR-COM-02 | Low | Khi Kế toán chốt cổng |
| Phân hệ 7 | Streak, XP & Thông báo đẩy | FR-RET-01 đến 03 | Medium | MVP |
| Phân hệ 8 | CMS, Phân quyền, Nhật ký, Đề thi | FR-ADM-01 đến 06 | High | MVP |
| Phân hệ 9 | Thi IELTS & Bài thi khóa | FR-EXAM-01 đến 03 | High | MVP |
| Phân hệ 10 | Hỗ trợ học viên | FR-SUP-01 đến 03 | High (FR-SUP-03), Medium (FR-SUP-01, 02) | MVP |
| Kiến trúc NFR | Hiệu năng, Bảo mật, Mở rộng 24 nguyên tắc, Mobile | NFR-PERF, NFR-SEC, NFR-SCAL, NFR-MOB | High | Toàn chu kỳ |
