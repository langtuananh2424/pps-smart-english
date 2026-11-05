# **TÀI LIỆU YÊU CẦU SẢN PHẨM SƠ BỘ (PRD v0.1)**

NỀN TẢNG BÁN KHÓA HỌC & HỆ THỐNG BÀI TẬP THÔNG MINH  
*Dựa trên 24 Nguyên Tắc Vàng Khi Học Tiếng Anh — Chuyên gia Nguyễn Hữu Phúc*

# **1\. THÔNG TIN TỔNG QUAN DỰ ÁN**

* **Tên dự án (Dự kiến):** PPS Smart English (B2C Retail Platform)  
* **Tác giả phương pháp học:** Chuyên gia Nghiên cứu Phát triển & Đào tạo Ngôn ngữ Anh hàng đầu Nguyễn Hữu Phúc.  
* **Đơn vị phát triển:** Nhóm Kỹ thuật & Sản phẩm PPS.  
* **Giai đoạn tài liệu:** Bản PRD sơ bộ (Draft v0.1) — Xác định khung nghiệp vụ, các module chức năng đã thống nhất, phương án kế thừa kiến trúc backend, danh mục câu hỏi chờ quyết định, và định hướng thiết kế giao diện (UI/UX).

# **2\. TẦM NHÌN SẢN PHẨM & GIÁ TRỊ CỐT LÕI (PRODUCT VISION)**

Hệ thống là một ứng dụng bán lẻ (B2C) độc lập, cung cấp các khóa học tiếng Anh tự học kết hợp hệ thống bài tập thực hành sâu và phản hồi AI tức thì.

## **2.1. Điểm khác biệt so với LMS trường học hiện tại (pps-education)**

* **Đối tượng người dùng:** Người học đại chúng tự do đăng ký, mua khóa học và tự luyện tập (không phụ thuộc vào danh sách lớp hay thời khóa biểu trường liên kết).  
* **Trải nghiệm mua hàng & Onboarding:** Luồng đánh giá năng lực nhanh, định hướng mục tiêu cá nhân hóa, trải nghiệm học thử (Freemium), thanh toán trực tuyến thuận tiện.  
* **Giao diện người dùng (UI/UX):** Hiện đại, tinh gọn, thân thiện với nhiều độ tuổi, áp dụng cơ chế Gamification (Streak, XP, Huy hiệu) để duy trì động lực.  
* **Trợ lý AI đa năng:** Không chỉ chấm bài tự luận hay phát âm đơn thuần mà đóng vai trò người dẫn dắt (Scaffolding Mentor) giúp phát triển ý tưởng bài viết.

## **2.2. Lộ trình triển khai 24 Nguyên tắc vàng — Giai đoạn 1 (MVP)**

* **Học từ kết nối (Connective Words & Collocations):** Tiếp thu từ vựng và cấu trúc liên kết thông qua video ngữ cảnh ngắn (1–1.5 phút), hiểu bản chất câu chuyện và rèn luyện phản xạ dùng cụm từ tự nhiên.  
* **8 Yếu tố ngữ pháp (8 Grammar Elements):** Nắm vững và áp dụng 8 cấu trúc vào bài viết IELTS để đạt điểm cao, qua bài tập nhận diện cấu trúc, sửa lỗi, xếp khối câu và viết lại câu.  
* **Viết phát triển ý (Idea Development Writing with AI):** Giải quyết triệt để rào cản "bí ý" khi viết thông qua mô hình phỏng vấn dẫn dắt đa tầng (Scaffolding Q\&A), sau đó AI hỗ trợ sửa lỗi ngữ pháp và chấm điểm độ mạch lạc.  
* **Kiến trúc module hóa mở rộng (Extensible Architecture):** Giai đoạn 1 (MVP) tập trung phát triển trước 3 nguyên tắc đầu tiên. Cơ sở dữ liệu và cấu trúc bài học/bài tập được thiết kế module hóa linh hoạt để sẵn sàng mở rộng tuần tự cho 21 nguyên tắc còn lại trong các giai đoạn tiếp theo mà không làm xáo trộn kiến trúc hệ thống.

# **3\. CHÂN DUNG NGƯỜI DÙNG MỤC TIÊU (USER PERSONAS)**

* **Nhóm 1 — Học sinh phổ thông (Lớp 6 – 12):** Cần nắm chắc ngữ pháp để làm bài thi ở trường, muốn mở rộng vốn từ kết nối và rèn luyện kỹ năng viết đoạn văn chuẩn chỉ.  
* **Nhóm 2 — Người luyện thi chứng chỉ (IELTS 4.5 \-\> 6.5+, Cambridge):** Thường bị kẹt band điểm do hành văn rời rạc, thiếu từ nối học thuật, bí ý tưởng triển khai bài viết Writing Task 1 & Task 2\.  
* **Nhóm 3 — Sinh viên & Người đi làm mất gốc:** Cần phương pháp học trực quan, ngắn gọn, có thể học linh hoạt mọi lúc mọi nơi trên điện thoại, có trợ lý AI sửa lỗi 24/7.

# **4\. HÀNH TRÌNH TRẢI NGHIỆM NGƯỜI DÙNG (USER JOURNEYS)**

## **4.1. Luồng Khám phá & Đăng ký (Onboarding & Activation Flow)**

Khách vãng lai vào App/Web \-\> Xem danh mục khóa học phân theo Level/Topic \-\> Tự chọn trình độ hiện tại hoặc chọn khóa học mong muốn \-\> Thiết lập Mục tiêu học tập (linh hoạt theo từng loại khóa học) \-\> Đăng ký tài khoản (Google, Apple, SĐT) \-\> Trải nghiệm học thử (Freemium: Bài giảng mẫu \+ 3 lượt chấm AI) \-\> Mua khóa học / Thanh toán trực tuyến.

## **4.2. Luồng Học tập & Luyện tập Hàng ngày (Daily Learning Loop)**

Vào màn hình Khóa học \-\> Chọn Level (A1, A2, B1...) \-\> Chọn Topic (Consumerism, Environment, Daily Life...) \-\> Xem Video Từ kết nối (Nghe câu chuyện, xem phụ đề song ngữ, giải nghĩa từ trọng tâm) \-\> Làm bài tập Ngân hàng câu hỏi (8 Yếu tố ngữ pháp & Từ nối) \-\> Thực hành "Viết phát triển ý": Trả lời câu hỏi gợi mở \-\> Hoàn thiện bài viết \-\> Nhận kết quả chấm điểm & sửa lỗi chi tiết từ AI \-\> Nhận điểm kinh nghiệm (XP), duy trì chuỗi ngày học (Streak).

# **5\. ĐẶC TẢ YÊU CẦU CHỨC NĂNG (FUNCTIONAL REQUIREMENTS)**

## **5.1. Phân hệ 1: Tài khoản & Phân quyền (Auth & Profiles)**

* **FR-AUTH-01 (Đăng ký / Đăng nhập):** Hỗ trợ đăng nhập nhanh qua Google OAuth2, Apple ID, hoặc Email/SĐT kèm mật khẩu.  
* **FR-AUTH-02 (Cấp độ tài khoản \- User Tier):**  
  * *Tài khoản Phổ thông (Free/Trial):* Được truy cập bài test đầu vào, học thử 1–2 Topic đầu tiên của mỗi Level, có 3 lượt chấm bài AI miễn phí.  
  * *Tài khoản Khóa học (Paid/Premium):* Mở khóa đầy đủ toàn bộ bài giảng, bài tập của Level/Topic đã mua và hạn mức lượt dùng AI tương ứng.  
* **FR-AUTH-03 (Quản lý Hồ sơ & Mục tiêu):** Lưu trữ trình độ hiện tại, mục tiêu kỳ vọng, thời gian mục tiêu, lịch sử làm bài và thống kê năng lực.

## **5.2. Phân hệ 2: Khảo sát mục tiêu & Điều hướng khóa học (Goal & Course Navigation Engine)**

* **FR-TEST-01 (Hoãn triển khai Bài test đầu vào tự động \- Placement Test):** Tạm thời chưa đưa bài test đầu vào vào MVP nhằm giảm tối đa rào cản người dùng; chuyển sang giai đoạn 2 (Post-MVP). Người học chủ động tự đánh giá và chọn trình độ/khóa học phù hợp.  
* **FR-TEST-02 (Hệ quy chiếu mục tiêu linh hoạt theo khóa học \- Goal Benchmark Framework):**  
  * Mục tiêu được cấu hình đa dạng và linh hoạt tùy theo từng khóa học: Luyện thi chứng chỉ (Band điểm IELTS 5.5, 6.5+; Cambridge KET, PET, FCE); Học sinh phổ thông (Theo Khối lớp 6–12, thi vào 10, ôn thi THPT Quốc gia); Tiếng Anh ứng dụng/giao tiếp (Mất gốc \-\> Tự tin phản xạ, Giao tiếp công sở, Phỏng vấn xin việc).  
  * Thiết kế Data Model: Hỗ trợ cấu trúc linh hoạt (target\_type và target\_value) gắn liền với metadata của khóa học, không bị bó cứng vào một hệ quy chiếu đơn lẻ.  
* **FR-TEST-03 (Điều hướng & Đề xuất khóa học):** Dựa trên trình độ tự chọn và mục tiêu thiết lập, hệ thống đề xuất khóa học lẻ hoặc combo theo lộ trình tối ưu nhất.

## **5.3. Phân hệ 3: Hệ thống bài giảng & Nội dung (Course & Video LMS)**

* **FR-LMS-01 (Cấu trúc phân cấp):** Khóa học được phân tầng chặt chẽ: Level \-\> Topic \-\> Unit / Bài học.  
* **FR-LMS-02 (Trình phát Video Từ kết nối):**  
  * Tích hợp trình phát video chất lượng cao (lưu trữ Cloudflare R2 hoặc YouTube Embed riêng tư).  
  * Hiển thị phụ đề thông minh (interactive transcript), cho phép bấm vào từ vựng/từ kết nối để xem giải nghĩa, phát âm mẫu và ngữ cảnh sử dụng.  
  * Theo dõi tiến độ xem video (ghi nhận hoàn thành khi xem đạt \>= 80% thời lượng).

## **5.4. Phân hệ 4: Hệ thống bài tập đa dạng (Exercise Engine)**

Kế thừa các dạng bài từ core engine của pps-education và mở rộng trải nghiệm tương tác:

* **FR-EX-01 (Dạng bài Từ kết nối):**  
  * Trắc nghiệm chọn từ nối phù hợp với ngữ cảnh logic (Cause, Effect, Contrast, Addition).  
  * Nối 2 vế câu bằng từ liên kết thích hợp.  
  * Viết lại câu có sử dụng từ kết nối cho trước.  
* **FR-EX-02 (Dạng bài 8 Yếu tố ngữ pháp):**  
  * *8 yếu tố:* Mệnh đề quan hệ tính ngữ; Câu đảo ngữ; Câu bị động; Chủ ngữ giả; Mệnh đề phân từ; Mệnh đề danh từ; Sử dụng "seem, be likely, tend to"; Các cấu trúc quen thuộc khác. Đây là các cấu trúc áp dụng vào bài viết IELTS, không phải bài luyện từ loại.  
  * *Nhận diện cấu trúc (Structure Identification):* Xác định cấu trúc được dùng trong câu mẫu.  
  * *Tìm và sửa lỗi sai (Spot & Fix):* Xác định và sửa cấu trúc dùng sai trong câu.  
  * *Xếp khối tạo câu (Sentence Building):* Sắp xếp các cụm từ thành câu đúng cấu trúc.  
  * *Viết lại câu (Sentence Rewriting):* Viết lại câu cho trước bằng cấu trúc được yêu cầu.  
* **FR-EX-03 (Cơ chế chấm điểm tức thì):** Chấm tự động cho các bài tập dạng cấu trúc, hiển thị lời giải thích chi tiết vì sao đáp án đó đúng/sai.

## **5.5. Phân hệ 5: Trợ lý AI & Chấm bài tự động (AI Engine)**

* **FR-AI-01 (Tính năng "Viết phát triển ý" với AI \- Guided Scaffolding):**  
  * *Prompting:* Đưa ra câu hỏi dẫn dắt theo chủ đề của bài học.  
  * *Interactive Q\&A:* Học viên nhập câu trả lời; hệ thống đưa tiếp câu hỏi gợi mở đào sâu (Wh-questions, ví dụ, nguyên nhân).  
  * *Synthesize Draft:* Ghép nối các câu trả lời thô thành một bản nháp đoạn văn có tính liên kết.  
* **FR-AI-02 (Chấm bài tự luận AI chuyên sâu):**  
  * Kế thừa logic prompt writing-grading-system-prompt của PPS:  
  * Đánh dấu lỗi ngữ pháp/từ vựng trực tiếp trên câu trả lời gốc (markedAnswer).  
  * Trả về bản sửa lỗi chuẩn xác (correctedAnswer) giữ nguyên ý tưởng gốc của học viên.  
  * Báo cáo phân tích việc sử dụng Từ kết nối và đánh giá độ phát triển mạch lạc của các ý.  
  * Báo cáo các yếu tố trong 8 Yếu tố ngữ pháp đã dùng đúng hoặc sai, kèm gợi ý nâng cấp câu.  
  * Cảnh báo nếu bài viết đi lệch trọng tâm câu hỏi (gateNote).  
* **FR-AI-03 (Chấm phát âm & Luyện nói phản xạ \- Speaking Reflex):**  
  * Thu âm giọng đọc học viên, chuyển giọng nói thành văn bản (Speech-to-Text qua Whisper).  
  * Đánh giá độ phát âm chính xác, độ trôi chảy và mức độ phản xạ theo tiêu chuẩn rubric.  
* **FR-AI-04 (Kiểm soát hạn mức AI Token/Quota):**  
  * Mỗi tài khoản được cấp hạn mức lượt chấm bài viết/phát âm theo gói đã mua.  
  * Hệ thống ghi nhận lịch sử tiêu thụ token (ai\_grading\_token\_usage) để tối ưu chi phí vận hành.

## **5.6. Phân hệ 6: Thương mại & Quản lý gói học (E-Commerce & Commerce)**

* **FR-COM-01 (Quản lý Gói bán):** Hỗ trợ cấu hình bán khóa học theo Level lẻ, Combo theo lộ trình mục tiêu, hoặc gói nạp thêm lượt dùng AI (Add-on Quota).  
* **FR-COM-02 (Thanh toán trực tuyến):** Tạo đơn hàng, hiển thị mã VietQR động tự động kích hoạt tài khoản ngay sau khi thanh toán thành công (hoặc cổng VNPay/MoMo).  
* **FR-COM-03 (Kích hoạt & Phân quyền nội dung):** Tự động mở khóa các Level/Topic tương ứng trong hồ sơ người dùng ngay khi giao dịch hoàn tất.

## **5.7. Phân hệ 7: Giữ chân & Gamification (Retention & Engagement)**

* **FR-RET-01 (Chuỗi ngày học liên tục \- Daily Streak):** Đếm số ngày học viên vào học liên tục; gửi thông báo nhắc nhở trước khi chuỗi bị gián đoạn.  
* **FR-RET-02 (Hệ thống Điểm kinh nghiệm \- XP & Level):** Tặng XP khi hoàn thành video, đạt điểm cao trong bài tập, kích thích thăng cấp danh hiệu.  
* **FR-RET-03 (Thông báo đẩy \- Push Notifications):** Nhắc lịch học hàng ngày, thông báo kết quả chấm bài AI hoàn tất, thông báo ưu đãi gói học mới.

# **6\. YÊU CẦU PHI CHỨC NĂNG & KIẾN TRÚC HỆ THỐNG (NFR & ARCHITECTURE)**

## **6.1. Kiến trúc Kỹ thuật (Technical Architecture)**

* **Backend:** Xây dựng dịch vụ độc lập sử dụng Java 21 \+ Spring Boot 3, kế thừa mô hình phân lớp chuẩn mực (Controller \-\> Service \-\> Repository), cơ chế quản lý version database bằng Flyway, và cơ sở dữ liệu PostgreSQL.  
* **Frontend:**  
  * *Giai đoạn 1 (Web MVP):* Xây dựng Single Page App trên nền tảng hiện đại (React/Vite hoặc Next.js \+ TailwindCSS) chuẩn Responsive trên cả Desktop và Mobile Browser.  
  * *Giai đoạn 2 (Mobile App):* Ứng dụng di động đa nền tảng viết bằng Flutter (iOS & Android), sử dụng chung 100% hệ thống RESTful API từ Backend.  
* **Media & Lưu trữ:** Video và file âm thanh học viên nộp được lưu trữ an toàn trên Cloudflare R2 (chi phí băng thông \$0) hoặc nhúng mã hóa YouTube.  
* **AI Infrastructure:** Tích hợp LLM API qua hàng đợi bất đồng bộ (ReflexGradingQueueService / Worker) để tránh nghẽn thread người dùng khi AI xử lý bài chấm dài.

## **6.2. Tiêu chuẩn Hiệu năng & Bảo mật**

* **Thời gian phản hồi API thông thường:** \<= 200ms.  
* **Thời gian trả kết quả chấm bài AI:** \<= 3–5s (kèm hiệu ứng loading trực quan cho học viên).  
* **Bảo mật toàn diện:** Chuẩn mã hóa HTTPS/TLS, JWT Access Token & Refresh Token, kiểm soát chặt chẽ Rate Limit để ngăn chặn spam API AI.

# **7\. CÁC HẠNG MỤC CHƯA RÕ — CHỜ TRAO ĐỔI VỚI ĐÀO TẠO & GIÁM ĐỐC (OPEN QUESTIONS)**

| STT | Vấn đề / Câu hỏi cần làm rõ | Đơn vị phụ trách | Tác động kỹ thuật & Sản phẩm |
| :---- | :---- | :---- | :---- |
| 1 | **Chi tiết 8 yếu tố ngữ pháp:** Danh sách chính thức 8 yếu tố ngữ pháp theo tài liệu của thầy Phúc là gì? | Phòng Đào tạo | Cấu trúc bảng danh mục trong DB, taxonomy gắn tag câu hỏi và bộ lọc bài tập. |
| 2 | **Cấu trúc Bài test đầu vào:** \[ĐÃ XÁC NHẬN / TẠM HOÃN\] Tạm thời chưa đưa bài test đầu vào vào MVP để giảm rào cản trải nghiệm người dùng; chuyển sang phát triển ở Giai đoạn 2 (Post-MVP). | Phòng Đào tạo | Thiết kế thuật toán chấm điểm và logic phân loại Level tự động. |
| 3 | **Hệ quy chiếu mục tiêu & Phân loại đối tượng:** \[ĐÃ XÁC NHẬN\] Áp dụng hệ quy chiếu mục tiêu linh hoạt gắn với metadata từng khóa học (IELTS/Cambridge, Khối lớp 6–12/THPTQG, Tiếng Anh ứng dụng); thiết kế bảng mục tiêu linh hoạt với target\_type & target\_value. | Ban Giám đốc & Marketing | Định hình giọng văn, thiết kế UI và định hướng kho nội dung ban đầu. |
| 4 | **Mô hình đóng gói & Giá bán chính thức:** Ưu tiên bán theo Khóa mua đứt sở hữu theo thời hạn (6–12 tháng), bán Combo lộ trình, hay gói Thuê bao (Subscription)? | Ban Giám đốc | Thiết kế Data Model cho đơn hàng, hóa đơn, thời hạn bản quyền và chu kỳ thanh toán. |
| 5 | **Định mức lượt dùng AI:** Mỗi gói khóa học sẽ cấp bao nhiêu lượt chấm bài AI miễn phí mỗi tháng? Giá bán gói nạp thêm lượt (Add-on)? | Ban Giám đốc | Thiết kế bảng user\_ai\_quota, logic trừ quota và cảnh báo khi sắp hết lượt. |

# **8\. QUYẾT ĐỊNH & ĐỀ XUẤT VỀ THIẾT KẾ GIAO DIỆN (UI/UX DESIGN DIRECTION)**

Khác biệt hoàn toàn với giao diện ERP/quản lý nội bộ trường học nhiều bảng biểu và nút bấm phức tạp, App bán lẻ hướng đến đại chúng cần sự thanh thoát, tập trung vào việc học và tạo cảm giác tiến bộ rõ rệt.

## **8.1. Phong cách thiết kế (Design Style & Aesthetic)**

* **Phong cách chủ đạo:** Modern EdTech & Clean Minimalism (Hiện đại, tối giản, thông thoáng, kết hợp các yếu tố tương tác thân thiện như Duolingo/MasterClass/Notion).  
* **Định hướng đa lứa tuổi:**  
  * Tránh hai thái cực: Không quá hoạt họa, trẻ con (khiến người lớn cảm thấy không nghiêm túc) và không quá hàn lâm, khô cứng (khiến học sinh phổ thông cảm thấy áp lực).  
  * Giải pháp: Sử dụng các khối thẻ bo góc mềm mại, icon minh họa dạng phẳng hiện đại (Flat/Duotone Icons), đồ họa vector tinh tế, và khoảng trắng (white space) rộng rãi để người học không bị mỏi mắt.

## **8.2. Bảng màu nhận diện (Color Palette)**

* **Bảng màu chính thức được chọn:** Phương án 1 — "Sunset Coral & Warm Amber" (Đỏ San Hô Ấm & Hổ Phách).  
* **Lý do lựa chọn:** Đa dụng, tiếp cận tự nhiên đa lứa tuổi (từ học sinh phổ thông đến người lớn), đáp ứng sở thích gam màu nóng của Ban Giám đốc nhưng không gây chói mắt hay xung đột với các màu trạng thái hệ thống.  
* **Bảng mã màu chi tiết:**  
  * **Primary Brand / CTA Color:** Đỏ San Hô Ấm (Sunset Coral) \#E05638 (hoặc \#FF7A59 theo bản sắc PPS) — Dùng cho các nút hành động chính (Bắt đầu học, Tiếp tục bài học, Mua khóa học), tab điều hướng active.  
  * **Secondary / Energy Color:** Cam Hổ Phách (Warm Amber / Gold) \#F59E0B (hoặc \#F4B740) — Dùng cho ngọn lửa chuỗi ngày học liên tục (Streak), huy hiệu sao và cấp độ.  
  * **Highlight Accent:** Vàng Nắng \#FBBF24 — Dùng cho các chi tiết thành tích xuất sắc, tag nổi bật.  
  * **Background Canvas:** Nền ngà dịu mắt (Warm Ivory Canvas) \#FAF8F6 — Giảm mỏi mắt khi học lâu, tạo cảm giác như trang giấy ấm áp.  
  * **Typography / Contrast:** Xanh đen mực viết (Deep Ink Navy) \#1E2A45 / \#1F2937 — Đảm bảo độ tương phản cao, rõ ràng, dễ đọc trên mọi thiết bị.  
  * **Semantic Feedback:** Xanh lá thành công \#16A34A, Đỏ lỗi sai \#DC2626 (tách biệt rõ với màu thương hiệu san hô), Tím gợi ý AI \#7C3AED.  
* **Quy tắc thiết kế UI đi kèm:**  
  * Áp dụng tỷ lệ 60-30-10: 60% Nền trung tính dịu mắt, 30% Thẻ bài học trắng tinh khiết \#FFFFFF kèm viền mảnh, 10% Điểm nhấn màu nóng.  
  * Bo góc 20px (Squircle) cho toàn bộ thẻ và nút bấm để tạo cảm giác thân thiện, mềm mại.

## **8.3. Kiểu chữ (Typography)**

* **Font chữ hệ thống:** Inter hoặc Plus Jakarta Sans (kết hợp Be Vietnam Pro cho tiếng Việt).  
* **Đặc điểm:** Font hình học không chân hiện đại, độ cao chữ lớn (x-height cao), đọc rõ ràng trên cả màn hình điện thoại 5 inch lẫn máy tính 27 inch.  
* **Phân cấp cỡ chữ (Type Scale):**  
  * Tiêu đề màn hình (Headings): 24px – 28px (Bold).  
  * Tiêu đề bài học / Topic: 18px – 20px (Semi-bold).  
  * Nội dung câu hỏi & bài giảng (Body Text): Tối thiểu 16px (Line height 1.6) để học viên đọc bài dài không bị mỏi mắt.  
  * Chú thích & từ loại (Tags / Labels): 12px – 13px (Medium / Uppercase).

## **8.4. Bố cục & Các thành phần giao diện đặc trưng**

* **Trình phát Video tương tác:** Video nằm ở nửa trên; phụ đề và danh sách từ vựng/từ nối xuất hiện đồng bộ ở nửa dưới. Chạm vào bất kỳ cụm từ nối nào trên phụ đề sẽ tự động dừng video 3 giây và mở popup giải nghĩa nhanh.  
* **Khu vực làm bài "Viết phát triển ý":**  
  * Trên Web: Bố cục 2 cột (Split-view). Cột trái là Trợ lý AI phỏng vấn gợi ý từng câu hỏi nhỏ; Cột phải là bài viết hoàn chỉnh đang được tạo lập dần theo thời gian thực.  
  * Trên Mobile: Dạng luồng hội thoại trượt mượt mà (Chat-like conversational interface), kết thúc bằng màn hình xem toàn bài trước khi gửi chấm.  
* **Màn hình Kết quả chấm bài AI:** Hiển thị điểm số tổng quan theo hình tròn trực quan. Phần bài viết có chế độ xem trước/sau (Before & After): Học viên có thể chạm để xem sự khác biệt giữa câu gốc của mình và câu chuẩn ngữ pháp do AI hiệu đính.  
* **Các tương tác vi mô (Micro-interactions):** Hiệu ứng rung nhẹ (Haptic feedback) khi chọn đáp án đúng trên điện thoại. Hiệu ứng văng hoa giấy (Confetti) nhỏ khi kết thúc chuỗi bài tập hoặc duy trì Streak thành công.

