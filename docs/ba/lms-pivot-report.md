# Báo cáo: chuyển hướng sang "Bán khóa học + LMS"

Cập nhật: 2026-11-19 · Căn cứ: D-97..D-123 trong `decisions.md` · Mô hình dữ liệu đề xuất: `lms-model.md` · Tài liệu phái sinh, không thay PRD/SRS; PRD/SRS chưa cập nhật phần LMS.

## 1. Tóm tắt

- Hệ thống có **hai loại khóa trên một nền chung** (D-117): khóa tự học và khóa có lớp (khóa học + LMS, sản phẩm chính). Mô hình là **khóa học → lớp**, khác pps-education (trường → lớp); chỉ tham khảo, không bê nguyên.
- **Nhiều chương trình** (IELTS, TOEIC…), admin tạo khóa qua giao diện; các chương trình cùng mô hình, khác thang điểm và lộ trình (D-105).
- **Làm web trước, app điện thoại sau** (D-123): web khóa tự học 8 tuần, web đầy đủ 17 tuần, thêm app 20 tuần, với 2 lập trình viên. Thời điểm đưa vào sử dụng do cấp trên quyết định.
- **Chi phí AI thấp**: tối đa khoảng 1.300–2.600 đ mỗi học viên mỗi tháng. Khoản lớn hơn là hạ tầng và tài khoản Zoom/Meet, chưa có báo giá.
- Còn **4 câu hỏi BA mở** (BQ-17..BQ-20) và một số việc chờ Kế toán, Phòng Đào tạo, pháp chế (mục 7).

## 2. Hai loại khóa

| | Khóa tự học | Khóa có lớp |
|---|---|---|
| Nội dung | Bài giảng video, bài tập sẵn, luyện tập thêm, kiểm tra theo chương và cuối khóa (D-118) | Như khóa tự học, thêm bài được giao |
| Học tuần tự | Admin bật/tắt theo khóa (D-119, D-122) | Như bên trái; giáo viên được mở sớm unit cho lớp hoặc từng em |
| Người dạy | Không | Giáo viên, trợ giảng kiêm gia sư 1-1 (D-99, D-108) |
| Buổi học | Không | Online: lịch + link Meet/Zoom; offline: báo giờ (D-106) |
| Cách bán | Mua trên app/web | Mua trên app/web hoặc qua tư vấn viên + link kích hoạt (D-107) |
| Thời hạn | Tính từ lúc mua | Phần tự học tính từ lúc mua; phần lớp theo lịch lớp (D-110) |

Quy tắc chung:

- **Xong một unit** = xem hết video và làm xong bài tập; kiểm tra chương phải làm, không cần đạt (D-122).
- **Giao bài**: chọn từ bài đã soạn cho khóa; không có bài giáo viên tự soạn, không nộp file (D-112). Điểm AI hiện ngay, giáo viên sửa và nhận xét sau (D-113). Nộp trễ vẫn chấm, đánh dấu trễ (D-121).
- **Điểm danh**: giáo viên, trợ giảng điểm tay (D-100).
- **Lượt AI**: bài được giao dùng quota riêng của khóa có lớp, không trừ lượt tuần (D-115).
- **Lớp**: mỗi lớp có ngày bắt đầu riêng, không có bảng đợt (D-120).

## 3. Vai trò

| Vai trò | Việc chính |
|---|---|
| Học viên | Học, làm bài, xem lịch, nộp bài được giao |
| Giáo viên, trợ giảng | Trong lớp được phân công: xem tiến độ, điểm danh, giao bài, sửa điểm, nhận xét, mở sớm unit; trợ giảng kiêm kèm 1-1 (D-99, D-108) |
| Quản lý đào tạo (giáo vụ) | Tạo lớp, xếp và chuyển lớp, phân công người dạy, xếp lịch kèm 1-1, xem báo cáo mọi lớp (D-109) |
| Kế toán | Nhận báo cáo số buổi dạy của từng người, kể cả cộng tác viên; hệ thống không tính lương (D-111) |

- **Không có tài khoản phụ huynh** (D-114, thay D-101). Đăng ký bắt buộc ngày sinh; dưới 18 tuổi cần phụ huynh chấp thuận điều khoản, từ 18 tuổi dùng bộ điều khoản khác. Cách chấp thuận: BQ-17.
- **Phạm vi**: chỉ khóa online bán trên app/web; không quản lý trung tâm offline hiện có, không bán LMS cho đơn vị khác (D-104).

## 4. Lịch triển khai (D-123)

| Phần việc | Thời gian | Tuần làm | Người |
|---|---|---|---|
| 1. Web khóa tự học | 23/11/2026 – 17/01/2027 | 8 | 2 dev |
| Chuẩn bị lớp học: PRD/SRS, Figma | 07/12/2026 – 10/01/2027 | 5 | BA, thiết kế (song song) |
| 2. Web khóa có lớp | 18/01 – 21/03/2027 | 7 | 1 dev |
| 3. App điện thoại | 18/01 – 11/04/2027 | 10 | 1 dev (song song) |
| Nghỉ Tết | 01/02 – 14/02/2027 | 2 | — |
| **Tổng web** | 23/11/2026 – 21/03/2027 | 17 (gồm Tết) | ≈ 3,9 tháng |
| **Tổng web + app** | 23/11/2026 – 11/04/2027 | 20 (gồm Tết) | ≈ 4,6 tháng |

- Ước tính, sai số khoảng 20–30%, không có dự phòng. Thứ tự này thay D-07 (mobile song song từ đầu) và D-103.
- Marketing bắt đầu tháng 12/2026, trước khi web khóa tự học xong (17/01/2027). Chủ dự án chọn giữ lịch, không tách bản web riêng cho marketing.
- Code phần lớp học chỉ bắt đầu khi PRD/SRS phần LMS đã được duyệt.

**Dời sau các giai đoạn trên**: AI soạn nháp nhận xét và tóm tắt tiến độ lớp; ticket và chat với nhân viên; import câu hỏi Excel; luyện thêm (#32); các mục P2, P3; video live trong app.

**Điều kiện giữ lịch**:

| Hạn | Việc | Ai |
|---|---|---|
| 15/12/2026 | Chốt chương trình làm trước, kèm thang điểm (BQ-20) | Chủ dự án, Phòng Đào tạo |
| 31/12/2026 | Đủ nội dung các khóa đầu tiên | Phòng Đào tạo |
| 10/01/2027 | Đặc tả luyện tập mới (BQ-18, D-116) | Phòng Đào tạo |
| 10/01/2027 | Duyệt PRD/SRS phần lớp học | Chủ dự án |

Mỗi điều kiện trễ 1 tuần thì lịch lùi khoảng 1 tuần.

## 5. Chi phí AI

Đơn giá Haiku 5.5: 0,10 USD / 1 triệu token đầu vào, 0,50 USD đầu ra; Sonnet 5.5 (chỉ dùng soạn đề): 2 / 10 USD. Quy đổi giả định 1 USD = 26.000 đ. Số token chưa đo thật.

| Việc | USD | VNĐ |
|---|---|---|
| Chấm một bài viết | ~0,0015 | ~40 đ |
| Chấm một phần bài nói (phần AI chữ) | ~0,001 | ~26 đ |
| Một học viên dùng hết lượt, cả tháng | 0,05–0,10 | 1.300–2.600 đ |

| Số học viên | USD/tháng | VNĐ/tháng |
|---|---|---|
| 500 | 40–65 | 1,0–1,7 triệu |
| 2.000 | 150–250 | 3,9–6,5 triệu |
| 10.000 | 750–1.250 | 19,5–32,5 triệu |

- Lượt AI có giới hạn mỗi học viên nên chi phí không vượt các mức trên; thực tế thấp hơn vì đa số không dùng hết lượt. Mục tiêu chi phí AI dưới 10% doanh thu gần như chắc chắn đạt.
- Quota riêng cho bài được giao (D-115) cộng thêm chi phí; mức quota chờ BQ-19.
- **Chưa có báo giá**: máy chủ chấm phát âm, nhận dạng giọng nói (Groq), tài khoản Zoom/Meet, lưu trữ video.

## 6. Rủi ro

1. **Phân quyền theo lớp**: giáo viên chỉ được xem lớp mình; dễ lộ dữ liệu nhất. Gom kiểm tra về một chỗ chung, có test chứng minh (mục P0 checklist production).
2. **Phạm vi tăng so với lịch**: nhiều chương trình (thang điểm, quy đổi, gợi ý khóa hiện cố định theo band IELTS), nhiều bài kiểm tra mỗi khóa (hiện chỉ một), học tuần tự, luyện tập mới chưa có đặc tả.
3. **Marketing đi trước sản phẩm**: tháng 12/2026 đã chạy marketing, web khóa tự học đến 17/01/2027 mới xong.
4. **Thanh toán**: đang dùng cổng giả lập; bán thật cần cổng thanh toán của Kế toán. Trước đó có thể bán qua tư vấn viên + link kích hoạt (đã có).
5. **Tải chấm nói**: cả lớp nộp cùng hạn trong khi máy chấm nói chạy 2 bài một lúc, RAM chưa đo (Q-25).
6. **Dữ liệu người dưới 18 tuổi**: Nghị định 13/2023; nội dung điều khoản và cách phụ huynh chấp thuận cần pháp chế xác nhận (BQ-17).
7. **Dạy online qua link ngoài** (D-106): app không biết thời gian tham gia thật; điểm danh dựa vào giáo viên.

## 7. Câu hỏi còn mở

| Mã | Câu hỏi | Ai trả lời |
|---|---|---|
| BQ-17 | Phụ huynh chấp thuận điều khoản bằng cách nào; nội dung hai bộ điều khoản | Pháp chế |
| BQ-18 | Hạn giao đặc tả luyện tập mới | Phòng Đào tạo |
| BQ-19 | Mức quota AI mặc định cho bài được giao | Chủ dự án |
| BQ-20 | Làm chương trình nào trước; ai cung cấp thang điểm, lộ trình | Chủ dự án, Phòng Đào tạo |
| BQ-01, BQ-03 | Giá gói 12 tháng, giá gói lượt AI | Chủ dự án, Kế toán |
| D-18 | Thời điểm kết nối cổng thanh toán thật | Kế toán |
| — | Thời điểm đưa web vào sử dụng | Cấp trên |
| — | Ngân sách máy chủ chấm nói, Zoom/Meet | Chủ dự án, Kế toán |

## 8. Bước tiếp theo

1. Soạn bản nháp bổ sung PRD/SRS phần LMS (lớp học, giao bài, điểm danh, buổi học, nhiều chương trình, học tuần tự) để chủ dự án duyệt trước 10/01/2027.
2. Cập nhật `docs/system-design.md` theo `lms-model.md` sau khi PRD/SRS được duyệt.
3. Tạo issue cho phần LMS trên GitHub Project khi phạm vi đã duyệt.
4. Thiết kế Figma màn giáo viên, giáo vụ và "Lớp của tôi" song song bước 1.
