# Backlog user story và tiêu chí chấp nhận

Phiên bản 0.1 · 2026-11-13 · Trạng thái: **Nháp chờ chủ dự án duyệt**

- Mẫu: *Là [tác nhân], tôi muốn [việc], để [giá trị]*. AC viết theo Given / When / Then.
- Cột **Nền** = phần nào cần làm: BE (backend), WEB, MOB (Flutter). Backend đã có thì ghi "BE có".
- Ưu tiên P0–P3 định nghĩa ở [roadmap.md](roadmap.md). Use case: [use-cases.md](use-cases.md).
- Mọi số liệu trong AC có dấu `*` là số tạm, chờ BQ tương ứng.

## Epic E1 — Tài khoản và hồ sơ

| ID | Story | FR | Ưu tiên | Nền |
|---|---|---|---|---|
| US-101 | Là khách, tôi muốn đăng ký bằng email hoặc Google để bắt đầu học | FR-AUTH-01 | P0 | BE có · WEB · MOB |
| US-102 | Là học viên, tôi muốn xác thực email và đặt lại mật khẩu khi quên | FR-AUTH-01 | P0 | BE có · WEB · MOB |
| US-103 | Là học viên, tôi muốn xem và đăng xuất các phiên đăng nhập | FR-AUTH-02 | P2 | BE có · WEB · MOB |
| US-104 | Là học viên, tôi muốn xem hồ sơ: khóa đang học, hạn, streak, XP, danh hiệu, lượt AI | FR-AUTH-03 | P0 | BE có (thiếu lượt theo khóa) · WEB · MOB |
| US-105 | Là học viên iOS, tôi muốn đăng nhập bằng Apple | FR-AUTH-01 | P1 | BE · MOB |

**AC US-101**
- Given khách nhập email hợp lệ và mật khẩu ≥ 8 ký tự, When bấm Đăng ký, Then tài khoản được tạo ở trạng thái chưa xác thực và email xác thực được gửi.
- Given email đã tồn tại, When đăng ký, Then báo "Email đã được dùng", không lộ thông tin khác.
- Given khách đã làm Q&A trước khi đăng ký, When đăng ký xong, Then câu trả lời Q&A được ghi vào hồ sơ.
- Không cấp lượt AI khi đăng ký (Q-27).

## Epic E2 — Định hướng: Q&A, kiểm tra đầu vào, gợi ý khóa

| ID | Story | FR | Ưu tiên | Nền |
|---|---|---|---|---|
| US-201 | Là khách, tôi muốn trả lời vài câu hỏi để được tư vấn khóa phù hợp nhu cầu | FR-GOAL-01 | P0 | BE (sửa) · WEB · MOB |
| US-202 | Là học viên, tôi muốn làm bài kiểm tra đầu vào miễn phí để biết level | FR-TEST-01 | P0 | BE mới · WEB · MOB |
| US-203 | Là học viên, tôi muốn xem band từng kỹ năng và khóa được gợi ý sau khi làm kiểm tra | FR-TEST-02, FR-GOAL-03 | P0 | BE mới · WEB · MOB |
| US-204 | Là Phòng Đào tạo, tôi muốn sửa bộ câu hỏi Q&A và bảng ánh xạ band → khóa | FR-GOAL-01, 03 | P1 | BE · WEB admin |

**AC US-202**
- Given học viên đã đăng nhập và có đề đầu vào xuất bản, When bấm Bắt đầu, Then đồng hồ đếm ngược theo thời gian cấu hình của đề và không có nút tạm dừng.
- Given đang làm bài và đóng trình duyệt, When mở lại trước khi hết giờ, Then các câu đã trả lời còn nguyên và thời gian còn lại tính theo máy chủ.
- Given hết giờ, When học viên chưa nộp, Then hệ thống tự nộp những câu đã lưu.
- Given bài có phần Writing/Speaking, When chấm AI, Then không trừ lượt AI của học viên.

**AC US-203**
- Given band tổng 4.5, When xem kết quả, Then gợi ý khóa "Academic English 2 (4.0 → 5.0)".
- Given band mục tiêu trong Q&A là 6.5 và band hiện tại 4.5, When xem kết quả, Then gợi ý thêm lộ trình các khóa kế tiếp tới khóa có đầu ra ≥ 6.5.
- Given chưa làm kiểm tra đầu vào, When mở gợi ý, Then gợi ý theo trình độ tự đánh giá và có nút mời làm kiểm tra.

## Epic E3 — Khóa học và bài giảng

| ID | Story | FR | Ưu tiên | Nền |
|---|---|---|---|---|
| US-301 | Là khách, tôi muốn xem danh mục 6 khóa, giá, mô tả, cây Level/Topic | FR-LMS-01 | P0 | BE có · WEB · MOB |
| US-302 | Là học viên chưa mua, tôi muốn học thử Topic đầu của khóa | FR-AUTH-02 (PRD) | P0 | BE có · WEB · MOB |
| US-303 | Là học viên, tôi muốn xem video từ kết nối, hệ thống ghi tiến độ và hoàn thành khi xem ≥ 80% | FR-LMS-02, 03 | P0 | BE có · WEB · MOB |
| US-304 | Là học viên, tôi muốn xem phụ đề song ngữ chạy theo video | FR-LMS-02 | P1 | BE mới · WEB · MOB |
| US-305 | Là học viên, tôi muốn chạm từ nối trong phụ đề để xem nghĩa, nghe phát âm | FR-LMS-02 | P1 | BE mới · WEB · MOB |
| US-306 | Là học viên, tôi muốn đọc to từ trong popup và được chấm phát âm từng âm | FR-AI-05 | P3 | MOB trước (Q-36) |
| US-307 | Là học viên, tôi muốn "Xem tiếp" bài đang học dở | FR-LMS-03 | P1 | BE có · WEB · MOB |

**AC US-302**
- Given học viên chưa mua khóa X, When mở Topic học thử của X, Then xem được video và làm bài tự chấm.
- Given cùng học viên, When làm câu cần AI hoặc mở Topic khác, Then thấy màn mời mua khóa, không lộ nội dung.

## Epic E4 — Bài tập Luyện tập PPS

| ID | Story | FR | Ưu tiên | Nền |
|---|---|---|---|---|
| US-401 | Là học viên, tôi muốn làm các dạng tự chấm (10.01–10.22, 10.24, 10.25, 10.33, 10.34, 10.36, 10.38) và thấy Đúng / Cần sửa kèm giải thích | FR-EX-04, 03 | P0 | BE (mở rộng) · WEB · MOB |
| US-402 | Là học viên, tôi muốn được thử lại khi sai lần đầu (nhánh thử lại) | FR-EX-04 | P1 | BE · WEB · MOB |
| US-403 | Là học viên, tôi muốn tạm dừng bộ bài tập luyện tập và làm tiếp sau | FR-EX-06 | P0 | BE mới · WEB · MOB |
| US-404 | Là học viên, tôi muốn viết tự luận (10.26) và viết lại với từ nối (10.37) được AI chấm | FR-EX-04, FR-AI-02 | P0 | BE có · WEB · MOB |
| US-405 | Là học viên, tôi muốn làm dạng nói (10.23, 10.27, 10.35) và video phản xạ (10.28–10.31) được AI chấm | FR-AI-03 | P1 | BE mới · WEB · MOB |
| US-406 | Là học viên, tôi muốn "Luyện thêm" theo dạng bài tôi chọn | FR-EX-07 | P1 | BE · WEB · MOB |
| US-407 | Là học viên, tôi muốn viết phát triển ý cùng AI (hỏi gợi mở → bản nháp → chấm) | FR-AI-01 | P0 | BE có · WEB · MOB |

**AC US-403**
- Given bộ bài ở chế độ Luyện tập có giới hạn 20 phút, When học viên bấm Tạm dừng ở phút 8, Then câu trả lời và 12 phút còn lại được lưu.
- Given bộ đã tạm dừng, When mở lại, Then tiếp tục từ câu đang làm với 12 phút.
- Given bộ ở chế độ Kiểm tra, When làm bài, Then không có nút Tạm dừng.

**AC US-401**
- Given câu Điền từ có đáp án "because of", When học viên nhập "Because of " , Then chấm đúng (không phân biệt hoa thường, bỏ khoảng trắng thừa).
- Given học viên nộp bộ, When chấm xong, Then thời gian chấm mỗi câu tự động < 100 ms và XP, streak được cập nhật.

## Epic E5 — IELTS: luyện tập và thi

| ID | Story | FR | Ưu tiên | Nền |
|---|---|---|---|---|
| US-501 | Là học viên, tôi muốn luyện Reading/Listening trong giao diện giống thi trên máy | FR-EX-05 | P1 | BE mới · WEB · MOB |
| US-502 | Là học viên, tôi muốn Cam gợi ý khi tôi bí ở chế độ Luyện tập, và kéo Cam để thu gọn | FR-AI-06 | P1 | BE · WEB · MOB |
| US-503 | Là học viên, tôi muốn luyện Writing Task 1, 2 và được AI chấm band | FR-EX-05, FR-AI-02 | P1 | BE (rubric band) · WEB · MOB |
| US-504 | Là học viên, tôi muốn luyện Speaking Part 1–3 và được AI chấm band | FR-EX-05, FR-AI-03 | P2 | BE mới · WEB · MOB |
| US-505 | Là học viên đã mua khóa, tôi muốn thi bài thi của khóa có giờ, không tạm dừng | FR-EXAM-02 | P1 | BE mới · WEB · MOB |
| US-506 | Là học viên, tôi muốn xem lại các câu trước khi nộp và xác nhận kết thúc | FR-EXAM-01 | P1 | WEB · MOB |
| US-507 | Là học viên, tôi muốn xem band từng kỹ năng và đáp án sau khi nộp | FR-EXAM-03 | P1 | BE · WEB · MOB |
| US-508 | Là học viên mobile, tôi muốn làm Reading ở màn hình ngang | FR-EX-05 | P2 | MOB · WEB responsive |

**AC US-505**
- Given học viên chưa mua khóa X, When mở bài thi của X, Then thấy màn mời mua khóa.
- Given khóa đã hết hạn, When mở bài thi, Then không được bắt đầu bài mới.
- Given đang thi, When hết giờ, Then bài tự nộp; Reading/Listening có band ngay.
- Given phần Writing, When nộp, Then trừ lượt theo trọng số* ở ví tuần của khóa X; thiếu lượt thì dùng pool.

**AC US-507**
- Given Reading đúng 30/40, When chấm, Then band lấy theo bảng quy đổi Phòng Đào tạo đã nhập cho đề đó.
- Given band 4 kỹ năng 6.0, 6.5, 5.5, 6.0, When tính band tổng, Then = 6.0 (trung bình 6.0).

## Epic E6 — Lượt AI và mua hàng

| ID | Story | FR | Ưu tiên | Nền |
|---|---|---|---|---|
| US-601 | Là học viên, tôi muốn mỗi khóa đã mua có lượt AI mỗi tuần, tự nạp lại thứ Hai | FR-AI-04 | P0 | BE (sửa) · WEB · MOB |
| US-602 | Là học viên, tôi muốn thấy lượt còn lại của tuần theo từng khóa và pool mua thêm | FR-AI-04 | P0 | BE · WEB · MOB |
| US-603 | Là học viên, tôi muốn mua gói lượt 10/20/50/100/200/500 khi hết lượt | FR-COM-05 | P1 | BE · WEB · MOB |
| US-604 | Là học viên, tôi muốn mua khóa 6 hoặc 12 tháng | FR-COM-01 | P0 | BE có (thiếu chọn thời hạn) · WEB · MOB |
| US-605 | Là học viên, tôi muốn gia hạn khóa sắp hết hạn | FR-COM-06 | P1 | BE · WEB · MOB |
| US-606 | Là học viên, tôi muốn kích hoạt khóa bằng link khuyến mãi | FR-COM-04 | P0 | BE có · WEB · MOB |
| US-607 | Là học viên, tôi muốn thanh toán bằng QR và khóa mở ngay | FR-COM-02 | P3 (chờ cổng) | BE · WEB · MOB |
| US-608 | Là học viên, tôi muốn xem lịch sử đơn hàng | FR-COM-01 | P1 | BE có · WEB · MOB |

**AC US-601**
- Given học viên có khóa X với hạn mức 30*/tuần đã dùng 30, When sang 00:00 thứ Hai giờ Việt Nam, Then ví tuần của X về 30 lượt chưa dùng.
- Given ví tuần còn 5 lượt, When sang tuần mới, Then 5 lượt cũ không cộng thêm.
- Given học viên có khóa X và Y, When chấm bài trong X, Then chỉ ví của X bị trừ.
- Given ví tuần của X còn 1, pool còn 10, When chấm Task 2 (3 lượt*), Then X về 0, pool còn 8.
- Given cả hai không đủ, When gửi chấm, Then từ chối trước khi gọi AI và hiện nút mua thêm.
- Given AI lỗi, When tác vụ thất bại, Then lượt đã giữ được hoàn lại.

## Epic E7 — Giữ chân

| ID | Story | FR | Ưu tiên | Nền |
|---|---|---|---|---|
| US-701 | Là học viên, tôi muốn thấy streak trên thanh điều hướng | FR-RET-01 | P1 | BE có · WEB · MOB |
| US-702 | Là học viên, tôi muốn nhận XP và lên danh hiệu | FR-RET-02 | P1 | BE có · WEB · MOB |
| US-703 | Là học viên, tôi muốn được nhắc lúc 20:00 nếu hôm nay chưa học | FR-RET-03 | P2 | BE có (Web Push) · FCM mới |
| US-704 | Là học viên, tôi muốn được báo khi bài AI chấm xong | FR-RET-03 | P1 | BE có · WEB · MOB |

## Epic E8 — Hỗ trợ học viên

| ID | Story | FR | Ưu tiên | Nền |
|---|---|---|---|---|
| US-801 | Là học viên, tôi muốn hỏi Cam trong chatbox các câu đơn giản (hạn khóa, cách dùng, lượt AI) | FR-SUP-01 | P2 | BE mới · WEB · MOB |
| US-802 | Là học viên, tôi muốn bấm "Gặp nhân viên" để mở ticket và chat với nhân viên | FR-SUP-02 | P1 | BE mới · WEB · MOB |
| US-803 | Là nhân viên, tôi muốn xem hàng chờ ticket, nhận và trả lời, đóng ticket | FR-SUP-02 | P1 | BE · WEB admin |
| US-804 | Là nhân viên, tôi muốn tra cứu học viên theo email / mã đơn và xem toàn bộ tình trạng | FR-SUP-03 | P0 | BE · WEB admin |
| US-805 | Là nhân viên, tôi muốn kích hoạt khóa thủ công, cộng lượt bù, mở khóa tài khoản kèm lý do | FR-SUP-03 | P0 | BE · WEB admin |
| US-806 | Là học viên, tôi muốn thấy Zalo và hotline trong mục Hỗ trợ | FR-SUP-01 | P0 | WEB · MOB |

**AC US-802**
- Given học viên đang chat với Cam, When bấm "Gặp nhân viên", Then ticket `OPEN` được tạo kèm lịch sử chat và học viên thấy thông báo thời gian phản hồi dự kiến.
- Given nhân viên trả lời, When học viên không mở app, Then học viên nhận thông báo đẩy.
- Given câu hỏi về thanh toán hoặc hoàn tiền, When hỏi Cam, Then Cam đề nghị gặp nhân viên, không tự hứa.

**AC US-805**
- Given nhân viên có quyền cộng lượt, When cộng 10 lượt cho học viên, Then bắt buộc nhập lý do, pool tăng 10 và nhật ký ghi người làm, thời điểm, lý do.
- Given nhân viên không có quyền, When gọi API, Then bị từ chối 403.

## Epic E9 — Quản trị

| ID | Story | FR | Ưu tiên | Nền |
|---|---|---|---|---|
| US-901 | Là Admin, tôi muốn tạo role, tick quyền theo nhóm, gán role cho nhân sự | FR-ADM-04 | P0 | BE mới · WEB admin |
| US-902 | Là Admin, tôi muốn cấp thêm hoặc chặn một quyền riêng cho một người, có hạn | FR-ADM-04 | P1 | BE · WEB admin |
| US-903 | Là Admin / Giám đốc, tôi muốn xem nhật ký thao tác | FR-ADM-05 | P0 | BE mới · WEB admin |
| US-904 | Là Phòng Đào tạo, tôi muốn quản lý khóa, Level, Topic, bài học, video, phụ đề | FR-ADM-01 | P0 | BE có (thiếu upload, phụ đề) · WEB admin |
| US-905 | Là Phòng Đào tạo, tôi muốn soạn câu hỏi 39 dạng và xem trước | FR-ADM-02 | P0 | BE (mở rộng) · WEB admin |
| US-906 | Là Phòng Đào tạo, tôi muốn import câu hỏi từ Excel | FR-ADM-02 | P1 | BE · WEB admin |
| US-907 | Là Phòng Đào tạo, tôi muốn tạo đề đầu vào, đề thi khóa, cấu hình thời gian, bảng quy đổi band | FR-ADM-06 | P0 | BE mới · WEB admin |
| US-908 | Là Admin, tôi muốn quản lý sản phẩm, giá, lượt/tuần, gói lượt | FR-COM-07 | P0 | BE · WEB admin |
| US-909 | Là Admin / Hỗ trợ, tôi muốn tạo và thu hồi link kích hoạt | FR-COM-04 | P0 | BE có · WEB admin |
| US-910 | Là Giám đốc / Kế toán, tôi muốn xem dashboard doanh thu, người dùng mới, tỷ lệ mua, chi phí AI | FR-ADM-03 | P1 | BE có · WEB admin |
| US-911 | Là Phòng Đào tạo, tôi muốn soạn FAQ cho Cam | FR-SUP-01 | P2 | BE · WEB admin |

**AC US-901**
- Given Admin tạo role "Kế toán" và tick nhóm "Đơn hàng: xem, đối soát", When gán role cho nhân sự A, Then lần gọi API kế tiếp của A có đúng các quyền đó.
- Given chỉ còn một người có quyền quản trị role, When gỡ quyền của người đó, Then hệ thống chặn.
- Mỗi quyền hiển thị một câu mô tả tiếng Việt.

## Epic E10 — Nền tảng và phi chức năng

| ID | Story | NFR | Ưu tiên |
|---|---|---|---|
| US-1001 | Khung app Flutter: đăng nhập, điều hướng, gọi API, lưu token an toàn | NFR-MOB-01, Q-13 | P0 |
| US-1002 | Load test 1.000 user* (BQ-05), API thường ≤ 200 ms | NFR-PERF-01 | P1 |
| US-1003 | Backup CSDL tự động hằng ngày, thử khôi phục | NFR-AVAIL-02 | P1 |
| US-1004 | Giám sát và cảnh báo (Q-23) | NFR-AVAIL-01 | P1 |
| US-1005 | Chặn nhúng video ngoài domain | NFR-SEC-02 | P2 |
| US-1006 | Kiểm tra WCAG 2.1 AA trên các màn chính | NFR-USAB-02 | P2 |
| US-1007 | Deploy staging | — | P1 (sau khi có màn FE đầu) |
