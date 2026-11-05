# **ĐẶC TẢ YÊU CẦU PHẦN MỀM (SRS v1.0)**

# **DỰ ÁN: NỀN TẢNG BÁN KHÓA HỌC & HỆ THỐNG BÀI TẬP THÔNG MINH (B2C)**

Dựa trên Phương pháp 24 Nguyên tắc vàng khi học Tiếng Anh — Chuyên gia Nguyễn Hữu Phúc

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
* **Nền tảng mục tiêu:** Giai đoạn 1 triển khai Web App Responsive (Desktop & Mobile Browser); Giai đoạn 2 phát hành Mobile App Native đa nền tảng bằng Flutter (iOS & Android).

## **1.3 Thuật ngữ & Từ viết tắt (Definitions & Acronyms)**

| Thuật ngữ / Viết tắt | Định nghĩa chi tiết |
| :---- | :---- |
| **B2C** | Business-to-Consumer: Mô hình thương mại hướng trực tiếp đến người học đại chúng. |
| **SRS** | Software Requirements Specification: Tài liệu đặc tả yêu cầu phần mềm tiêu chuẩn. |
| **Scaffolding** | Phương pháp sư phạm "giàn giáo", phỏng vấn dẫn dắt từng bước giúp người học tự xây dựng bài viết hoàn chỉnh. |
| **Connective Words** | Các từ/cụm từ liên kết, collocations, cấu trúc liên từ tạo mạch văn tự nhiên. |
| **Freemium** | Mô hình cho phép trải nghiệm miễn phí giới hạn trước khi trả phí mua khóa. |
| **Token / Quota** | Hạn mức lượt gọi API trí tuệ nhân tạo (LLM/Whisper) được cấp phát cho từng tài khoản. |
| **VietQR** | Chuẩn thanh toán quét mã QR động chuyển khoản ngân hàng tự động đối soát tại Việt Nam. |

## **1.4 Tài liệu tham khảo (References)**

* Sách & Phương pháp luận "24 Nguyên Tắc Vàng Khi Học Tiếng Anh" — Tác giả Nguyễn Hữu Phúc.  
* Tài liệu PRD v0.1 — Nền tảng Bán Khóa Học & Hệ Thống Bài Tập Thông Minh PPS.  
* Mã nguồn và Kiến trúc hệ thống `pps-education` (Spring Boot 3, Flyway, PostgreSQL, Cloudflare R2).

# **2\. MÔ TẢ TỔNG QUAN HỆ THỐNG (OVERALL DESCRIPTION)**

## **2.1 Bối cảnh & Kiến trúc tổng thể (Product Perspective)**

Hệ thống vận hành theo kiến trúc Client-Server hiện đại, phi tập trung (decoupled architecture):

* **Backend Core:** Dịch vụ RESTful API viết bằng Java 21 \+ Spring Boot 3, cơ sở dữ liệu PostgreSQL 16 quản lý phiên bản bằng Flyway, caching dữ liệu bằng Redis.  
* **Frontend Clients:** Web Client (React 18 \+ Vite \+ TailwindCSS) và Mobile Client (Flutter 3.x), cả hai kết nối độc lập với Backend Core qua giao thức HTTPS/REST và WSS/SSE.  
* **Hạ tầng Media & CDN:** Video bài giảng và file âm thanh học viên nộp được lưu trữ an toàn trên Cloudflare R2 kết hợp YouTube Private Embed.  
* **AI Engine:** Tích hợp LLM API (OpenAI / Claude / Gemini) và Whisper STT thông qua hàng đợi bất đồng bộ (Asynchronous Queue Worker) để xử lý chấm điểm và hội thoại.  
* **Cổng thanh toán:** Kết nối cổng tạo mã VietQR động tự động kích hoạt khóa học (PayOS / VNPay / MoMo).

## **2.2 Các tác nhân hệ thống (User Classes & Actors)**

* **Khách vãng lai (Guest):** Người dùng chưa đăng nhập; được duyệt danh mục khóa học, xem bài giảng mẫu, làm bài tập thử.  
* **Học viên Phổ thông (Free/Trial Student):** Đã đăng ký tài khoản; sở hữu quyền học thử 1–2 Topic đầu tiên của mỗi Level và được cấp 3 lượt chấm bài viết AI miễn phí.  
* **Học viên Trả phí (Paid/Premium Student):** Đã mua khóa học; được mở khóa toàn bộ bài giảng, toàn bộ bài tập và hạn mức lượt dùng AI theo gói đã thanh toán.  
* **Quản trị viên / Biên tập viên Đào tạo (Admin/Content Creator):** Sử dụng cổng Admin CMS để quản lý Level, Topic, upload video, soạn ngân hàng câu hỏi, cấu hình rubric chấm AI và theo dõi doanh thu.  
* **Hệ thống AI bên ngoài (External AI Services):** LLM Provider (chấm Writing, hội thoại Scaffolding) và Whisper Provider (chuyển âm thanh thành chữ chấm Speaking).  
* **Hệ thống Cổng thanh toán (Payment Gateway):** Tiếp nhận thông tin đơn hàng và gửi webhook xác thực giao dịch thành công.

## **2.3 Môi trường vận hành (Operating Environment)**

* **Web Client:** Chạy trên các trình duyệt hiện đại (Chrome 110+, Safari 16+, Edge 110+, Firefox 110+) trên Desktop, Tablet và Mobile.  
* **Mobile Client:** Hỗ trợ hệ điều hành iOS 15.0+ và Android 10.0+ (API Level 29+).  
* **Hạ tầng Server:** Container Linux (Dockerized), Kubernetes/Docker Compose, RAM tối thiểu 8GB, CPU 4 Cores.

# **3\. YÊU CẦU GIAO DIỆN BÊN NGOÀI (EXTERNAL INTERFACE REQUIREMENTS)**

## **3.1 Giao diện người dùng (User Interface \- UI/UX)**

* **Bảng màu nhận diện chính thức (Sunset Coral & Warm Amber):**  
  * Primary Brand / CTA: Đỏ San Hô Ấm `#E05638`.  
  * Secondary / Streak / Badges: Cam Hổ Phách `#F59E0B`.  
  * Highlight Accent: Vàng Nắng `#FBBF24`.  
  * Background Canvas: Nền ngà dịu mắt `#FAF8F6` mô phỏng trang sách giấy.  
  * Typography: Xanh đen mực viết `#1E2A45` (tương phản chuẩn WCAG AAA).  
  * Semantic Colors: Thành công `#16A34A`, Lỗi ngữ pháp `#DC2626`, Gợi ý AI `#7C3AED`.  
* **Quy chuẩn bố cục:** Bo góc lớn 20px (Squircle), áp dụng tỷ lệ thị giác 60-30-10, vùng chạm tối thiểu 48x48px trên thiết bị di động.

## **3.2 Giao diện phần mềm & Giao thức (Software & Protocol Interfaces)**

* **Giao diện CSDL:** PostgreSQL JDBC Driver qua HikariCP connection pool.  
* **Giao diện Lưu trữ:** AWS S3 SDK tương thích Cloudflare R2 Object Storage.  
* **Giao thức mạng:** 100% kết nối qua HTTPS/TLS 1.3; Server-Sent Events (SSE) cho phản hồi thời gian thực từ AI.

# **4\. ĐẶC TẢ YÊU CẦU CHỨC NĂNG (FUNCTIONAL REQUIREMENTS)**

## **PHÂN HỆ 1: XÁC THỰC & HỒ SƠ HỌC VIÊN (MODULE 1: AUTHENTICATION & PROFILES)**

| Mã Yêu Cầu | Tên Chức Năng | Mô Tả Nghiệp Vụ & Quy Tắc Thực Thi |
| :---- | :---- | :---- |
| **FR-AUTH-01** | Đăng ký tài khoản | Hỗ trợ đăng ký qua Google OAuth2, Apple Sign-in hoặc Email/Số điện thoại kèm mật khẩu. Tự động khởi tạo hồ sơ Free Tier kèm 3 token AI dùng thử. |
| **FR-AUTH-02** | Đăng nhập & Quản lý phiên | Cấp phát JWT Token (Access Token hiệu lực 15 phút, Refresh Token hiệu lực 30 ngày lưu trong HttpOnly Cookie). Hỗ trợ đăng xuất và hủy token từ xa. |
| **FR-AUTH-03** | Quản lý Hồ sơ & Streak | Cho phép cập nhật thông tin cá nhân, theo dõi chuỗi ngày học liên tục (Streak), số XP tích lũy và điều chỉnh mục tiêu đầu ra linh hoạt theo từng khóa học. |

## **PHÂN HỆ 2: KHẢO SÁT MỤC TIÊU & ĐIỀU HƯỚNG KHÓA HỌC (MODULE 2: GOAL & CATALOG NAVIGATION)**

| Mã Yêu Cầu | Tên Chức Năng | Mô Tả Nghiệp Vụ & Quy Tắc Thực Thi |
| :---- | :---- | :---- |
| **FR-GOAL-01** | Khảo sát nhu cầu tự chọn | Người dùng mới có thể chọn nhanh trình độ tự ước lượng (Mất gốc / Cơ bản / Trung cấp / Khá) mà không bắt buộc làm bài kiểm tra đầu vào tại Phase 1\. |
| **FR-GOAL-02** | Hệ quy chiếu mục tiêu động | Lưu trữ mục tiêu theo cặp `(target_type, target_value)` linh hoạt: IELTS (Band 5.5–7.0+), Phổ thông (Lớp 6–12, Vào 10, THPTQG), Cambridge (KET, PET, FCE), Giao tiếp. |
| **FR-GOAL-03** | Đề xuất khóa học thông minh | Tự động phân tích mục tiêu đã chọn để gợi ý khóa học đơn lẻ hoặc gói combo lộ trình tương ứng với nhu cầu của học viên. |

## **PHÂN HỆ 3: QUẢN LÝ & TRÌNH PHÁT BÀI GIẢNG VIDEO TỪ KẾT NỐI (MODULE 3: COURSE & VIDEO LMS)**

| Mã Yêu Cầu | Tên Chức Năng | Mô Tả Nghiệp Vụ & Quy Tắc Thực Thi |
| :---- | :---- | :---- |
| **FR-LMS-01** | Cấu trúc phân cấp nội dung | Tổ chức khóa học theo mô hình cây: `Course` \-\> `Level` \-\> `Topic` \-\> `Lesson` (Video Từ kết nối / Video Phản xạ). |
| **FR-LMS-02** | Trình phát Video tương tác | Streaming video độ trễ thấp từ Cloudflare R2 / YouTube Private; phụ đề tương tác song ngữ chạy đồng bộ. Chạm vào từ kết nối trong phụ đề sẽ dừng 3s và mở popup giải nghĩa \+ phát âm. |
| **FR-LMS-03** | Theo dõi tiến độ xem video | Tự động ghi nhận thời lượng xem; đánh dấu bài học hoàn thành khi học viên theo dõi đạt từ 80% thời lượng trở lên. |

## **PHÂN HỆ 4: NGÂN HÀNG CÂU HỎI & ĐỘNG CƠ BÀI TẬP TƯƠNG TÁC (MODULE 4: MULTI-TYPE EXERCISE ENGINE)**

| Mã Yêu Cầu | Tên Chức Năng | Mô Tả Nghiệp Vụ & Quy Tắc Thực Thi |
| :---- | :---- | :---- |
| **FR-EX-01** | Bài tập Học từ kết nối | Bao gồm: Trắc nghiệm logic ngữ cảnh (Cause, Effect, Contrast, Addition); Ghép nối 2 vế câu bằng liên từ; Điền từ nối vào chỗ trống trong đoạn văn ngữ cảnh. |
| **FR-EX-02** | Bài tập 8 Yếu tố ngữ pháp | 8 yếu tố là các cấu trúc áp dụng vào bài viết IELTS: Mệnh đề quan hệ tính ngữ; Câu đảo ngữ; Câu bị động; Chủ ngữ giả; Mệnh đề phân từ; Mệnh đề danh từ; Sử dụng "seem, be likely, tend to"; Các cấu trúc quen thuộc khác. Bài tập bao gồm: Nhận diện cấu trúc trong câu mẫu; Tìm và sửa lỗi sai (Spot & Fix); Xếp khối tạo câu đúng cấu trúc; Viết lại câu bằng cấu trúc yêu cầu. |
| **FR-EX-03** | Chấm điểm & Giải thích tức thì | Tự động chấm điểm bài tập cấu trúc tức thì (\< 100ms) kèm lời giải thích ngữ pháp chi tiết cho các phương án đúng và sai. |

## **PHÂN HỆ 5: TRỢ LÝ AI VIẾT PHÁT TRIỂN Ý & CHẤM CHỮA BÀI (MODULE 5: AI WRITING & EVALUATION ENGINE)**

| Mã Yêu Cầu | Tên Chức Năng | Mô Tả Nghiệp Vụ & Quy Tắc Thực Thi |
| :---- | :---- | :---- |
| **FR-AI-01** | Dẫn dắt phát triển ý (Scaffolding) | Bước 1: Hiển thị câu hỏi dẫn dắt (Root Question). Bước 2: AI hỏi sâu 2–3 câu hỏi tiếp nối dựa trên câu trả lời của học viên. Bước 3: AI tự động tổng hợp câu trả lời thành bản nháp hoàn chỉnh có từ kết nối. |
| **FR-AI-02** | Chấm bài tự luận AI chuyên sâu | Kế thừa rubric PPS: Bôi đỏ lỗi sai trên bài gốc (`markedAnswer`), đưa ra bản sửa chuẩn (`correctedAnswer`) bảo toàn ý tưởng gốc, phân tích tần suất/độ chính xác của từ kết nối, đánh giá các yếu tố trong 8 Yếu tố ngữ pháp đã dùng đúng hoặc sai kèm gợi ý nâng cấp câu, cảnh báo lạc đề (`gateNote`). |
| **FR-AI-03** | Chấm phát âm & Phản xạ nói | Thu âm câu trả lời qua mic; sử dụng Whisper STT chuyển thành văn bản; chấm điểm độ chính xác phát âm, độ trôi chảy và thời gian phản xạ theo rubric. |
| **FR-AI-04** | Kiểm soát Quota & Token AI | Kiểm tra số dư token trước khi gọi AI; ghi nhận chi tiết số lượng token tiêu hao vào bảng `ai_grading_token_usage` để kiểm soát chi phí API. |

## **PHÂN HỆ 6: THƯƠNG MẠI & QUẢN LÝ GÓI HỌC (MODULE 6: E-COMMERCE & PAYMENTS)**

| Mã Yêu Cầu | Tên Chức Năng | Mô Tả Nghiệp Vụ & Quy Tắc Thực Thi |
| :---- | :---- | :---- |
| **FR-COM-01** | Quản lý Giỏ hàng & Đơn hàng | Hỗ trợ mua khóa học lẻ theo Level, mua Combo lộ trình, hoặc mua các gói Token Add-on nạp thêm lượt dùng AI. |
| **FR-COM-02** | Thanh toán VietQR tự động | Sinh mã VietQR động chứa số tiền và mã giao dịch duy nhất; webhook đối soát tự động kích hoạt tài khoản trong vòng 3 giây không cần can thiệp thủ công. |
| **FR-COM-03** | Phân quyền & Kích hoạt bản quyền | Cập nhật trạng thái `PAID` trên hệ thống và mở khóa toàn bộ quyền truy cập nội dung bài giảng, bài tập tương ứng với gói đã mua. |
| **FR-COM-04** | Kích hoạt bằng link | Admin tạo, xem và thu hồi link kích hoạt gắn với một sản phẩm, có số lượt dùng tối đa và ngày hết hạn. Người dùng đã đăng nhập, đã xác thực email, kích hoạt một lần cho mỗi link và nhận đúng thời hạn, lượt AI của sản phẩm mà không cần thanh toán. |

## **PHÂN HỆ 7: GIỮ CHÂN & GAMIFICATION (MODULE 7: RETENTION & GAMIFICATION)**

| Mã Yêu Cầu | Tên Chức Năng | Mô Tả Nghiệp Vụ & Quy Tắc Thực Thi |
| :---- | :---- | :---- |
| **FR-RET-01** | Chuỗi ngày học liên tục (Streak) | Đếm số ngày học viên hoàn thành ít nhất 1 bài học/bài tập mỗi ngày; hiển thị huy hiệu ngọn lửa Streak tại vị trí nổi bật trên thanh điều hướng. |
| **FR-RET-02** | Điểm kinh nghiệm (XP) & Danh hiệu | Thưởng XP sau mỗi bài học hoàn thành; tích lũy XP để thăng hạng danh hiệu học tập (Tập sự, Tự tin, Chuyên gia). |
| **FR-RET-03** | Thông báo đẩy thông minh | Gửi thông báo đẩy qua Web Push / Mobile Push nhắc nhở trước 21h nếu chưa duy trì chuỗi Streak, hoặc thông báo khi bài viết đã được AI chấm xong. |

## **PHÂN HỆ 8: CỔNG QUẢN TRỊ NỘI DUNG CMS (MODULE 8: ADMIN CMS PORTAL)**

| Mã Yêu Cầu | Tên Chức Năng | Mô Tả Nghiệp Vụ & Quy Tắc Thực Thi |
| :---- | :---- | :---- |
| **FR-ADM-01** | Quản lý Khóa học & Bài giảng | Cho phép tạo Level, Topic, upload video trực tiếp lên Cloudflare R2 hoặc dán mã nhúng YouTube, tải lên và đồng bộ file phụ đề VTT/SRT. |
| **FR-ADM-02** | Quản lý & Import ngân hàng câu hỏi | Kế thừa công cụ import hàng loạt từ file Excel/Word của `pps-education`, hỗ trợ cấu hình gắn thẻ yếu tố ngữ pháp và thiết lập đáp án tự động. |
| **FR-ADM-03** | Báo cáo & Thống kê Doanh thu | Dashboard thời gian thực theo dõi số lượng người dùng mới, tỷ lệ chuyển đổi Freemium sang Paid, doanh thu theo gói học và chi phí tiêu thụ token AI. |

# **5\. ĐẶC TẢ YÊU CẦU PHI CHỨC NĂNG (NON-FUNCTIONAL REQUIREMENTS \- NFR)**

## **5.1 Hiệu năng & Khả năng đáp ứng (NFR-PERFORMANCE)**

* **NFR-PERF-01:** Thời gian phản hồi API trung bình cho các tác vụ tra cứu dữ liệu và nộp bài trắc nghiệm \$\\le 200\\text{ms}\$ ở điều kiện tải 1.000 người dùng đồng thời (CCU).  
* **NFR-PERF-02:** Thời gian khởi chạy phát video (buffer latency) \$\\le 1.0\\text{s}\$ nhờ hạ tầng mạng phân phối nội dung toàn cầu của Cloudflare.  
* **NFR-PERF-03:** Thời gian AI trả về kết quả chấm bài viết tự luận \$\\le 3\\text{–}5\\text{s}\$; trong thời gian chờ, giao diện bắt buộc hiển thị animation tiến độ trực quan.

## **5.2 Bảo mật & Toàn vẹn dữ liệu (NFR-SECURITY)**

* **NFR-SEC-01:** Toàn bộ mật khẩu người dùng phải được băm bằng thuật toán `BCrypt` với độ phức tạp salt rounds \$\\ge 12\$.  
* **NFR-SEC-02:** Bảo vệ bản quyền video: Video lưu trữ trên Cloudflare R2 chỉ được phân phối qua Pre-signed URL có thời hạn hiệu lực tối đa 120 phút; chặn tải trực tiếp và chặn nhúng ngoài domain hệ thống.  
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

# **6\. SƠ ĐỒ THỰC THỂ DỮ LIỆU CỐT LÕI (CONCEPTUAL DATA MODEL)**

Hệ thống kế thừa cấu trúc dữ liệu đã được kiểm chứng từ `pps-education` và bổ sung các thực thể phục vụ kinh doanh B2C:

* `users` & `user_profiles`: Thông tin tài khoản người dùng, phân quyền, mục tiêu học tập (`target_type`, `target_value`), điểm kinh nghiệm XP, chuỗi ngày học Streak.  
* `principles`: Danh mục 24 Nguyên tắc vàng (`code`: `PRIN_01` đến `PRIN_24`, `name`, `status`: ACTIVE/PLANNED).  
* `courses`, `course_levels`, `topics`, `lessons`: Cây cấu trúc tổ chức khóa học, chuyên đề và nội dung video bài giảng.  
* `question_banks`, `questions`, `question_choices`: Ngân hàng câu hỏi đa dạng loại hình (Từ kết nối, 8 Yếu tố ngữ pháp, Tự luận, Trắc nghiệm, Kéo thả, Xếp khối câu).  
* `orders`, `order_items`, `payment_transactions`: Quản lý giỏ hàng, thông tin đơn hàng, giao dịch VietQR và nhật ký webhook đối soát.  
* `user_course_enrollments`: Quản lý trạng thái và quyền truy cập nội dung bài giảng của từng học viên.  
* `student_exercise_attempts`, `student_answers`, `student_answer_grading`: Lịch sử làm bài, chi tiết câu trả lời và kết quả đánh giá chấm điểm.  
* `ai_grading_token_usage`: Nhật ký theo dõi mức độ tiêu thụ token của từng lượt gọi AI.

# **7\. MA TRẬN TRUY VẾT YÊU CẦU (TRACEABILITY MATRIX)**

| Mã Phân Hệ | Yêu Cầu PRD Tương Ứng | Mã FR / NFR Trong SRS | Mức Độ Ưu Tiên | Giai Đoạn Triển Khai |
| :---- | :---- | :---- | :---- | :---- |
| Phân hệ 1 | Quản lý Tài khoản & Quyền Freemium | FR-AUTH-01 đến 03 | High | MVP |
| Phân hệ 2 | Khảo sát Mục tiêu & Điều hướng | FR-GOAL-01 đến 03 | High | MVP |
| Phân hệ 3 | Bài giảng Video Từ kết nối | FR-LMS-01 đến 03 | High | MVP |
| Phân hệ 4 | Bài tập 8 Yếu tố ngữ pháp & Từ nối | FR-EX-01 đến 03 | High | MVP |
| Phân hệ 5 | AI Viết phát triển ý & Chấm chữa | FR-AI-01 đến 04 | High | MVP |
| Phân hệ 6 | Đơn hàng & Thanh toán VietQR | FR-COM-01 đến 04 | High | MVP |
| Phân hệ 7 | Streak, XP & Thông báo đẩy | FR-RET-01 đến 03 | Medium | MVP |
| Phân hệ 8 | CMS Quản trị & Import câu hỏi | FR-ADM-01 đến 03 | High | MVP |
| Kiến trúc NFR | Hiệu năng, Bảo mật, Mở rộng 24 nguyên tắc | NFR-PERF, NFR-SEC, NFR-SCAL | High | Toàn chu kỳ |

