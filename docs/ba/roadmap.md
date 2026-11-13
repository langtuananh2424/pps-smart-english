# Ưu tiên và tiến độ

Cập nhật lần cuối: 2026-11-13 · Người cập nhật: chủ dự án

## 1. Định nghĩa mức ưu tiên

| Mức | Nghĩa | Khi trễ |
|---|---|---|
| **P0** | Không có thì không ra mắt được | Lùi ngày ra mắt |
| **P1** | Cần có lúc ra mắt | Cân nhắc lùi ngày hoặc hạ xuống P2 |
| **P2** | Nên có; ra mắt thiếu vẫn dùng được | Đưa sang bản cập nhật đầu sau ra mắt |
| **P3** | Làm cuối hoặc chờ quyết định bên ngoài | Không chặn ra mắt |

## 2. Bảng ưu tiên theo hạng mục

| # | Hạng mục | FR | Ưu tiên | Phụ thuộc / chặn bởi |
|---|---|---|---|---|
| 1 | Phân quyền hybrid + nhật ký thao tác | FR-ADM-04, 05 | P0 | BQ-08 (role Giáo viên) |
| 2 | Sản phẩm 6 khóa, giá, thời hạn 6/12 tháng | FR-COM-07, 01 | P0 | BQ-01 |
| 3 | Lượt AI theo tuần, theo khóa | FR-AI-04 | P0 | BQ-04 |
| 4 | CMS khóa, bài học, upload video, phụ đề | FR-ADM-01 | P0 | Q-29 (đường truyền video) |
| 5 | Ngân hàng câu hỏi 39 dạng + soạn, xem trước | FR-EX-04, FR-ADM-02 | P0 | Figma từng dạng (đã có) |
| 6 | Thời gian làm bài, tạm dừng / làm tiếp | FR-EX-06 | P0 | — |
| 7 | Q&A tư vấn + kiểm tra đầu vào + gợi ý khóa | FR-GOAL-01, 03, FR-TEST-01, 02 | P0 | BQ-06; đề đầu vào từ Phòng Đào tạo |
| 8 | Web học viên: đăng nhập, danh mục, học thử, bài giảng, bài tập, hồ sơ | nhiều | P0 | Figma các màn còn thiếu (hồ sơ, mua) |
| 9 | Công cụ nhân viên hỗ trợ | FR-SUP-03 | P0 | #1 |
| 10 | Khung app Flutter | NFR-MOB-01 | P0 | Q-13 |
| 11 | Đề thi, bảng quy đổi band (CMS) | FR-ADM-06 | P0 | — |
| 12 | IELTS Reading/Listening luyện tập + Cam gợi ý | FR-EX-05, FR-AI-06 | P1 | #5, #11 |
| 13 | Bài thi khóa (mô phỏng thi) | FR-EXAM-01..03 | P1 | #11, BQ-07 |
| 14 | IELTS Writing chấm band | FR-EX-05, FR-AI-02 | P1 | Rubric band |
| 15 | Chấm nói (dạng nói PPS, video phản xạ) | FR-AI-03 | P1 | Q-16, Q-17, Q-25 |
| 16 | Phụ đề song ngữ + popup từ vựng | FR-LMS-02 | P1 | Q-07 |
| 17 | Ticket + chat với nhân viên | FR-SUP-02 | P1 | #9, BQ-09 |
| 18 | Gói lượt AI mua thêm, gia hạn khóa | FR-COM-05, 06 | P1 | BQ-02, BQ-03 |
| 19 | Luyện thêm | FR-EX-07 | P1 | #5 |
| 20 | Import câu hỏi Excel | FR-ADM-02 | P1 | Mẫu file của Phòng Đào tạo |
| 21 | Streak, XP, thông báo trên web/app | FR-RET-01..03 | P1 | BE có |
| 22 | Dashboard báo cáo | FR-ADM-03 | P1 | BQ-12 |
| 23 | Load test, backup, giám sát | NFR-PERF-01, NFR-AVAIL | P1 | BQ-05, Q-23 |
| 24 | IELTS Speaking luyện tập và thi | FR-EX-05, FR-AI-03 | P2 | #15 |
| 25 | Chatbox Cam + FAQ | FR-SUP-01 | P2 | BQ-09 |
| 26 | Trợ lý AI (Cam) hỏi đáp học tập | FR-AI-07 | P2 | Figma mục 11 |
| 27 | Reading màn hình ngang trên mobile | FR-EX-05 | P2 | #12 |
| 28 | Phát âm từng từ trên thiết bị | FR-AI-05 | P3 | Q-32..Q-36 |
| 29 | Cổng thanh toán thật | FR-COM-02 | P3 | D-18 (Kế toán) |

## 3. Cột mốc (thứ tự, chưa gắn ngày)

Ngày chưa ghi vì chưa biết số người làm. Chủ dự án điền cột "Ngày dự kiến" khi chốt nguồn lực.

| Mốc | Nội dung | Gồm hạng mục | Ngày dự kiến | Trạng thái |
|---|---|---|---|---|
| M1 | Nền quản trị: role, sản phẩm, lượt theo khóa, nhân viên | 1, 2, 3, 9 | | Chưa bắt đầu |
| M2 | Nội dung: CMS đủ để Phòng Đào tạo nhập 6 khóa, câu hỏi, đề | 4, 5, 11, 20 | | Chưa bắt đầu |
| M3 | Học viên web: từ đăng ký tới làm bài tập, thời gian, tạm dừng | 6, 7, 8 | | Chưa bắt đầu |
| M4 | IELTS và thi: luyện tập R/L/W, thi khóa | 12, 13, 14 | | Chưa bắt đầu |
| M5 | Nói, hỗ trợ, mua thêm: chấm nói, ticket, gói lượt, Luyện thêm, phụ đề | 15–19, 21 | | Chưa bắt đầu |
| M6 | App Flutter đủ tính năng học viên (chạy song song từ M1) | 10 + màn học viên | | Chưa bắt đầu |
| M7 | Sẵn sàng ra mắt: load test, backup, giám sát, staging, P2 nếu kịp | 22–27 | | Chưa bắt đầu |
| M8 | Cổng thanh toán thật, phát âm từng từ | 28, 29 | | Chờ quyết định |

Song song suốt từ M1: **Phòng Đào tạo soạn nội dung** (6 khóa, câu hỏi, đề đầu vào, đề thi khóa, bảng quy đổi, Q&A, FAQ). Đây là đường găng: thiếu nội dung thì không ra mắt được dù code xong.

## 4. Bảng theo dõi tiến độ

Trạng thái: `—` không cần · `Chưa` · `Đang làm` · `Xong` · `Chặn`.

| # | Hạng mục | Figma | BE | WEB | MOB | Nội dung | Ghi chú |
|---|---|---|---|---|---|---|---|
| 1 | Phân quyền hybrid + nhật ký | Chưa | Chưa | Chưa | — | — | Hiện có ADMIN/EDITOR cố định |
| 2 | Sản phẩm, giá, thời hạn | Chưa | Đang làm | Chưa | Chưa | Chưa | BE có `products`, thiếu chọn 6/12 tháng |
| 3 | Lượt AI theo tuần, theo khóa | Chưa | Chưa | Chưa | Chưa | — | BE đang là ví chung, cần sửa |
| 4 | CMS khóa, video, phụ đề | Chưa | Đang làm | Chưa | — | Chưa | BE có cây khóa, thiếu upload + phụ đề |
| 5 | Câu hỏi 39 dạng | Xong | Đang làm | Chưa | Chưa | Chưa | BE có 7 dạng |
| 6 | Thời gian, tạm dừng | Chưa | Chưa | Chưa | Chưa | — | |
| 7 | Q&A + đầu vào + gợi ý | Đang làm | Đang làm | Chưa | Chưa | Chưa | BE có mục tiêu, trình độ tự chọn |
| 8 | Web học viên cơ bản | Đang làm | Xong | Chưa | — | — | BE gần đủ |
| 9 | Công cụ nhân viên | Chưa | Chưa | Chưa | — | — | |
| 10 | Khung app Flutter | — | — | — | Chưa | — | |
| 11 | Đề thi, quy đổi band | Chưa | Chưa | Chưa | — | Chưa | |
| 12 | IELTS R/L luyện tập + Cam | Xong | Chưa | Chưa | Chưa | Chưa | |
| 13 | Bài thi khóa | Xong | Chưa | Chưa | Chưa | Chưa | |
| 14 | IELTS Writing chấm band | Xong | Đang làm | Chưa | Chưa | Chưa | BE có chấm viết theo rubric PPS |
| 15 | Chấm nói | Xong | Chưa | Chưa | Chưa | — | Chặn: Q-16, Q-17 |
| 16 | Phụ đề + popup từ | Xong | Chưa | Chưa | Chưa | Chưa | |
| 17 | Ticket + chat | Chưa | Chưa | Chưa | Chưa | — | |
| 18 | Gói lượt, gia hạn | Chưa | Chưa | Chưa | Chưa | — | Chặn: BQ-02, BQ-03 |
| 19 | Luyện thêm | Xong | Chưa | Chưa | Chưa | Chưa | |
| 20 | Import Excel | — | Chưa | Chưa | — | Chưa | |
| 21 | Streak, XP, thông báo | Chưa | Xong | Chưa | Chưa | — | |
| 22 | Dashboard | Chưa | Đang làm | Chưa | — | — | |
| 23 | Load test, backup, giám sát | — | Chưa | — | — | — | |
| 24 | IELTS Speaking | Xong | Chưa | Chưa | Chưa | Chưa | |
| 25 | Chatbox Cam + FAQ | Chưa | Chưa | Chưa | Chưa | Chưa | |
| 26 | Trợ lý AI | Xong | Chưa | Chưa | Chưa | — | |
| 27 | Reading ngang mobile | Xong | — | Chưa | Chưa | — | |
| 28 | Phát âm từng từ | Chưa | Chưa | Chưa | Chưa | Chưa | Chặn: Q-32..36 |
| 29 | Cổng thanh toán thật | Chưa | Chưa | Chưa | Chưa | — | Chặn: D-18 |

Trạng thái ban đầu do BA ước theo code và Figma ngày 2026-11-13, chưa kiểm chứng bằng chạy thử.

## 5. Công cụ theo dõi trực quan (đề xuất)

| Công cụ | Hợp khi | Ghi chú |
|---|---|---|
| **GitHub Projects** (đề xuất) | Code đã ở GitHub; muốn thẻ việc gắn thẳng với issue, PR, nhánh | Miễn phí; có dạng bảng Kanban, bảng tính, và Roadmap theo ngày; PR merge thì thẻ tự chuyển cột |
| Notion | Muốn Phòng Đào tạo, Kế toán cùng xem và cập nhật mà không cần biết GitHub | Database với view Board / Timeline; không tự nối với PR |
| Linear | Đội dev lớn hơn, cần sprint chặt | Trả phí khi đội lớn |

Gợi ý: dùng **GitHub Projects** cho việc của dev, mỗi hạng mục ở mục 2 là một issue (kèm user story từ [user-stories.md](user-stories.md)), cột trạng thái như mục 4, trường "Ưu tiên" và "Cột mốc". File này giữ làm bản tóm tắt.
