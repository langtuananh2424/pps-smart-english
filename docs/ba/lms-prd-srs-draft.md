# Bản nháp bổ sung PRD/SRS: phần LMS (khóa có lớp)

Ngày: 2026-11-19 · Trạng thái: **đã áp dụng vào PRD v0.3 và SRS v1.2 ngày 2026-11-19** (chủ dự án cho phép) · Căn cứ: D-97..D-124 (`decisions.md`), mô hình `lms-model.md`, báo cáo `lms-pivot-report.md`.

File này giữ lại để truy vết. Nguồn yêu cầu là `docs/PRD.md` và `docs/SRS.md`. Phần còn chờ trả lời liệt kê ở Phần D.

---

## Phần A. PRD v0.3

### A1. Mục 1 — thêm dòng cập nhật

> **Cập nhật v0.3 (dự kiến):** Chuyển hướng thành nền tảng bán khóa học kết hợp LMS theo D-97..D-123: hai loại khóa (tự học, có lớp), nhiều chương trình, giáo viên và trợ giảng, giao bài, điểm danh, học tuần tự. Đặc tả chi tiết: SRS v1.2.

### A2. Mục 2 — thay đoạn mở đầu

> Hệ thống là nền tảng B2C bán khóa học tiếng Anh trực tuyến, gồm hai loại khóa trên cùng một nền: **khóa tự học** (bài giảng, bài tập, luyện tập, kiểm tra, AI chấm) và **khóa có lớp** (khóa học + LMS: giáo viên, trợ giảng theo dõi, giao bài, dạy online, điểm danh). Khóa có lớp là sản phẩm chính (D-117).

### A3. Mục 2.1 — sửa gạch đầu dòng "Đối tượng người dùng"

> **Mô hình tổ chức:** pps-education phân cấp **trường → lớp** cho trường liên kết. PPS Smart English phân cấp **khóa học → lớp**: học viên mua khóa; khóa tự học không có lớp, khóa có lớp xếp học viên vào lớp có giáo viên. Chỉ phục vụ khóa online bán trên app/web; không quản lý trung tâm offline hiện có, không bán LMS cho đơn vị khác (D-104).

### A4. Mục 2.2 — thay gạch đầu dòng "Sản phẩm bán trong MVP" và "Kiểm tra đầu vào và bài thi khóa"

> **Sản phẩm bán:** khóa thuộc nhiều chương trình (IELTS, TOEIC…), admin tạo qua giao diện quản trị; mọi chương trình cùng mô hình, khác thang điểm và lộ trình (D-105). Mỗi khóa thuộc một trong hai loại: tự học hoặc có lớp (D-98). Chương trình làm trước: BQ-20.
>
> **Kiểm tra:** kiểm tra đầu vào miễn phí theo chương trình; mỗi khóa có nhiều bài kiểm tra: theo chương và cuối khóa (D-118).
>
> **Thứ tự làm:** web trước, app điện thoại sau (D-123). Thời điểm đưa vào sử dụng do cấp trên quyết định.

### A5. Mục 3 — thêm chân dung

> * **Giáo viên, trợ giảng:** nhân viên hoặc cộng tác viên dạy khóa có lớp; trợ giảng kiêm gia sư kèm 1-1 (D-108, D-111). Cần xem nhanh tiến độ cả lớp, giao bài, điểm danh, sửa điểm AI.
> * **Quản lý đào tạo (giáo vụ):** mở lớp, xếp học viên, phân công người dạy, theo dõi mọi lớp (D-109).
>
> Học viên có thể dưới 18 tuổi; khi đăng ký cần phụ huynh chấp thuận điều khoản (D-114). Không có tài khoản phụ huynh.

### A6. Mục 4 — thêm hai hành trình

> **4.3. Luồng học trong lớp:** Mua khóa có lớp (trên app/web, hoặc qua tư vấn viên và link kích hoạt) → học ngay phần tự học → giáo vụ xếp vào lớp → nhận thông báo lịch học (online có link, offline có giờ) → học theo buổi, làm bài được giao trước hạn → xem điểm AI ngay, nhận nhận xét của giáo viên sau → làm kiểm tra chương → hết lịch lớp, phần tự học còn đến hết thời hạn mua.
>
> **4.4. Luồng giảng dạy:** Giáo viên vào danh sách lớp được phân công → xem tiến độ từng học viên → giao bài từ nội dung có sẵn của khóa, đặt hạn → dạy buổi học, điểm danh → sửa điểm, nhận xét bài nộp → mở sớm unit khi cần. Kế toán lấy báo cáo số buổi dạy của từng người.

### A7. Mục 5 — thêm phân hệ

> **5.11. Phân hệ 11: Lớp học & Giảng dạy (Classes & Teaching)** — Lớp học thuộc khóa có lớp; giáo viên, trợ giảng phụ trách; buổi học nhóm và kèm 1-1, online hoặc offline; điểm danh tay; giao bài từ nội dung có sẵn, chấm AI, giáo viên sửa điểm; báo cáo tiến độ, chuyên cần, số buổi dạy. Đặc tả: SRS Phân hệ 11 (FR-CLS, FR-SES, FR-ASG).

### A8. Mục 7 — thêm câu hỏi mở

> BQ-17 (cách phụ huynh chấp thuận), BQ-18 (hạn đặc tả luyện tập mới), BQ-19 (quota AI bài được giao), BQ-20 (chương trình làm trước, thang điểm).

---

## Phần B. SRS v1.2

### B1. Mục 1.2 — thay các gạch đầu dòng

> * **Bối cảnh phân tách:** Hệ thống B2C độc lập với `pps-education`. pps-education tổ chức theo trường → lớp; hệ thống này tổ chức theo **khóa học → lớp**. Chỉ tham khảo pps-education ở phần lớp học, giao bài, điểm danh, không dùng lại mô hình trường.
> * **Hai loại khóa (D-117):** khóa tự học (SELF_PACED) và khóa có lớp (CLASS_BASED). Hai loại dùng chung cây nội dung, bài tập, kiểm tra, AI chấm.
> * **Sản phẩm bán:** khóa thuộc nhiều chương trình do admin tạo (D-105); mỗi khóa bán theo thời hạn 6 hoặc 12 tháng. Khóa có lớp có thêm lịch lớp (D-110). Không bán combo, không hoàn tiền.
> * **Nền tảng mục tiêu:** Web App Responsive làm trước; Mobile App Flutter làm sau, song song với phần khóa có lớp trên web (D-123). Màn quản trị, giáo viên, giáo vụ chỉ có trên web.

### B2. Mục 1.3 — thêm thuật ngữ

| Thuật ngữ | Nghĩa |
|---|---|
| Chương trình (Program) | Nhóm khóa cùng thang điểm và lộ trình, ví dụ IELTS, TOEIC |
| Khóa tự học | Khóa không có lớp, học viên tự học |
| Khóa có lớp | Khóa có lớp học, giáo viên, buổi học, bài được giao |
| Lớp (Class) | Nhóm học viên của một khóa có lớp, có ngày bắt đầu, kết thúc, hình thức online/offline |
| Buổi học (Session) | Một buổi nhóm hoặc kèm 1-1, có giờ, link (online) |
| Bài được giao (Assignment) | Bài lấy từ nội dung có sẵn của khóa, giao cho lớp hoặc từng học viên, có hạn nộp |
| Unit, chương | Cùng một cấp: Chủ đề (Topic) trong cây `Course → Level → Topic → Lesson` (D-124, tạm thời, chờ Phòng Đào tạo xác nhận cách thiết kế khóa) |
| Học tuần tự | Unit sau chỉ mở khi xong unit trước |
| Giáo vụ | Quản lý đào tạo, role mới |

### B3. Mục 2.2 — thêm tác nhân, sửa dòng Nhân sự nội bộ

> * **Giáo viên:** trong lớp được phân công: xem tiến độ, giao bài, điểm danh, sửa điểm và nhận xét, mở sớm unit (D-99).
> * **Trợ giảng:** cùng quyền thao tác như giáo viên trong lớp được phân công; kiêm gia sư kèm 1-1 (D-99, D-108).
> * **Quản lý đào tạo (giáo vụ):** tạo lớp, xếp và chuyển lớp, phân công giáo viên, trợ giảng, xếp lịch kèm 1-1, xem báo cáo mọi lớp (D-109).
> * Dòng **Nhân sự nội bộ**: thay "Giáo viên (việc của Giáo viên: BQ-08)" bằng "Giáo viên, Trợ giảng, Quản lý đào tạo (Phân hệ 11)". Giáo viên, trợ giảng có thể là cộng tác viên (D-111).

### B4. Sửa yêu cầu có sẵn

| Mã | Sửa thành |
|---|---|
| **FR-AUTH-01** | Thêm: đăng ký **bắt buộc ngày sinh**. Dưới 18 tuổi: phải có phụ huynh chấp thuận bộ điều khoản dành cho người dưới 18 trước khi tạo tài khoản; từ 18 tuổi: chấp thuận bộ điều khoản thường (D-114). Cách chấp thuận và nội dung điều khoản: BQ-17. Luồng lỗi: thiếu ngày sinh hoặc chưa chấp thuận thì không tạo tài khoản; ngày sinh ở tương lai hoặc không hợp lệ thì báo lỗi tại ô nhập. Đăng ký Google cũng phải bổ sung ngày sinh trước khi vào học. |
| **FR-GOAL-02** | Mục tiêu theo cặp `(chương trình, giá trị)`; giá trị theo thang điểm của chương trình, không cố định band IELTS (D-105). |
| **FR-GOAL-03** | Gợi ý khóa trong cùng chương trình theo dải đầu vào/đầu ra của khóa trên thang điểm của chương trình đó. |
| **FR-TEST-01, 02** | Kiểm tra đầu vào theo từng chương trình; kết quả theo thang điểm của chương trình (D-105). |
| **FR-LMS-01** | Thêm: khóa thuộc một **chương trình** và một **loại** (tự học / có lớp); admin tạo khóa qua giao diện quản trị. Mỗi khóa có cài đặt **học tuần tự** bật/tắt (D-122). |
| **FR-LMS-04 (mới)** | **Học tuần tự.** Khi khóa bật học tuần tự: bài giảng và bài tập của unit sau chỉ mở khi học viên **xong unit trước** = xem mọi bài giảng của unit đạt % thời lượng admin cài cho khóa (mặc định 80%, như FR-LMS-03) và đã nộp mọi bài tập sẵn của unit, không cần điểm tối thiểu. Kiểm tra chương (cuối mỗi unit) phải nộp mới mở unit sau, không cần đạt (D-122, D-124). Bài được giao nằm ở unit chưa mở thì mở riêng bài đó cho người được giao (FR-ASG-01). Giáo viên, trợ giảng được mở sớm unit cho cả lớp hoặc từng học viên (FR-CLS-06). Luồng lỗi: học viên mở trực tiếp nội dung chưa mở (qua link) thì máy chủ từ chối, không trả video hay câu hỏi; giao diện hiện unit đang khóa và việc còn thiếu. Khi tắt học tuần tự: mở hết như hiện tại. |
| **FR-EXAM-02** | Mỗi khóa có **nhiều bài kiểm tra**: kiểm tra theo chương và kiểm tra cuối khóa (D-118). Kiểm tra cuối khóa giữ quy tắc hiện tại (mở theo % hoàn thành, số lần thi lại). Kiểm tra chương: admin cài số lần làm lại và thời gian cho từng bài (D-124). |
| **FR-EXAM-03** | Quy đổi điểm theo thang của **chương trình**; quy tắc làm tròn band IELTS chỉ áp dụng cho chương trình IELTS (D-105). |
| **FR-AI-04** | Bài viết/nói do giáo viên giao trong khóa có lớp **không trừ lượt tuần** của học viên (D-115). Có giới hạn riêng hay không tính lượt: BQ-19 (chủ dự án nghiêng về không tính lượt). |
| **FR-COM-01** | Thêm sản phẩm **khóa có lớp**: mua trên app/web hoặc qua tư vấn viên và link kích hoạt (D-107). Mua xong được học phần tự học ngay; thời hạn phần tự học tính từ lúc mua; phần lớp theo lịch lớp (D-110). |
| **FR-ADM-04** | Thêm role **Trợ giảng**, **Quản lý đào tạo** và quyền: `class.manage` (giáo vụ), `class.teach` (giáo viên, trợ giảng, chỉ trong lớp được phân công), `report.teaching` (kế toán xem báo cáo số buổi dạy). Thay ma trận D-37 cho Giáo viên. |

### B5. Phân hệ 11 mới: LỚP HỌC & GIẢNG DẠY (MODULE 11: CLASSES & TEACHING)

Chỉ áp dụng cho khóa có lớp. Mọi thao tác của giáo viên, trợ giảng chỉ trong lớp mình đang được phân công (NFR-SEC-05).

| Mã | Tên | Mô tả nghiệp vụ, luồng chính và luồng lỗi |
|---|---|---|
| **FR-CLS-01** | Tạo và sửa lớp | Giáo vụ tạo lớp cho một khóa có lớp: tên, ngày bắt đầu, ngày kết thúc, hình thức (online/offline), sĩ số tối đa. Mỗi lớp có ngày bắt đầu riêng, không có khái niệm đợt riêng (D-120). Lỗi: khóa không phải khóa có lớp, ngày kết thúc trước ngày bắt đầu, sĩ số ≤ 0 thì từ chối. Không xóa lớp đã có học viên; chỉ đóng lớp. |
| **FR-CLS-02** | Danh sách chờ xếp lớp | Học viên đã mua khóa có lớp mà chưa có lớp hiện ở danh sách chờ của giáo vụ, kèm ngày mua. Trong lúc chờ, học viên học phần tự học (D-110). |
| **FR-CLS-03** | Xếp, chuyển, rút học viên | Giáo vụ xếp học viên vào lớp của đúng khóa; chuyển sang lớp khác cùng khóa; rút khỏi lớp. Một học viên chỉ có một lớp đang học mỗi khóa. Học viên nhận thông báo khi được xếp hoặc chuyển. Lỗi: lớp đầy, lớp đã đóng, học viên chưa mua khóa hoặc khóa hết hạn thì từ chối. Lịch sử xếp, chuyển được giữ. |
| **FR-CLS-04** | Phân công giáo viên, trợ giảng | Giáo vụ gán một giáo viên chính và một hoặc nhiều trợ giảng cho lớp, có ngày bắt đầu và kết thúc phụ trách. Mỗi lớp có đúng một giáo viên chính đang phụ trách. Đổi người: kết thúc phân công cũ, tạo phân công mới; người cũ mất quyền với lớp ngay. |
| **FR-CLS-05** | Bảng tiến độ lớp | Giáo viên, trợ giảng, giáo vụ xem từng học viên: % hoàn thành bài giảng, unit hiện tại, bài được giao đã nộp/chưa nộp/nộp trễ, điểm trung bình, điểm kiểm tra chương, tỷ lệ chuyên cần. Đánh dấu học viên tụt lại khi có một trong các dấu hiệu: quá hạn chưa nộp bài được giao, vắng nhiều buổi, unit đang học chậm hơn lớp, điểm thấp; giáo vụ cài ngưỡng theo khóa (D-124). |
| **FR-CLS-06** | Mở sớm unit | Giáo viên, trợ giảng mở một unit cho cả lớp hoặc từng học viên, bỏ qua điều kiện học tuần tự (D-122). Ghi người mở, thời điểm. Không áp dụng cho khóa tắt học tuần tự. |
| **FR-CLS-07** | Báo cáo số buổi dạy | Kế toán, giáo vụ xem số buổi và tổng giờ dạy của từng giáo viên, trợ giảng theo khoảng ngày, tách buổi nhóm và kèm 1-1. Chỉ đếm buổi đã điểm danh. Xuất được file. Hệ thống không tính lương (D-111). |
| **FR-SES-01** | Lịch buổi học nhóm | Giáo vụ hoặc giáo viên của lớp (D-124) tạo buổi học cho lớp: ngày giờ bắt đầu, kết thúc, người dạy. Buổi online có link Meet/Zoom do người tạo dán; buổi offline chỉ có thời gian (D-106). Tạo lặp theo tuần trong khoảng ngày của lớp. Lỗi: buổi nằm ngoài ngày của lớp, giờ kết thúc trước giờ bắt đầu, buổi online thiếu link thì từ chối. |
| **FR-SES-02** | Buổi kèm 1-1 | Giáo vụ xếp buổi kèm cho một học viên với một trợ giảng của lớp; cùng quy tắc giờ, link như FR-SES-01 (D-108). |
| **FR-SES-03** | Đổi, hủy buổi và thông báo | Giáo vụ hoặc giáo viên của lớp đổi giờ, đổi link, hủy buổi; học viên liên quan nhận thông báo. Nhắc lịch trước giờ học theo mốc admin cài (D-124). Không đổi được buổi đã điểm danh. |
| **FR-SES-04** | Điểm danh tay | Giáo viên, trợ giảng điểm danh từng học viên của buổi: có mặt, vắng, đi muộn, vắng có phép (D-100). Học viên không tự điểm danh. Sửa được điểm danh không giới hạn thời gian; mỗi lần sửa ghi người sửa, thời điểm, giá trị cũ (D-124). Báo cáo số buổi dạy (FR-CLS-07) hiện số buổi đã bị sửa sau khi xuất để Kế toán đối chiếu. Lỗi: điểm danh buổi chưa diễn ra thì từ chối. |
| **FR-ASG-01** | Giao bài | Giáo viên, trợ giảng chọn bài đã soạn và xuất bản cho khóa (bài tập sẵn, luyện tập, bài viết/nói AI chấm) và giao cho cả lớp hoặc một số học viên, kèm hạn nộp (D-112). Không giao trùng cùng bài, cùng lớp, cùng hạn. Học viên nhận thông báo. Không có bài giáo viên tự soạn, không nộp file. Lỗi: bài chưa xuất bản, hạn ở quá khứ, học viên không thuộc lớp thì từ chối. Bài được giao nằm ở unit chưa mở: tự mở riêng bài đó cho người được giao, không mở cả unit (D-124). |
| **FR-ASG-02** | Làm và nộp bài được giao | Học viên làm như bài thường; bài viết/nói trừ quota bài giao (FR-AI-04). Nộp sau hạn vẫn chấm, đánh dấu trễ (D-121). Nhắc hạn trước khi hết hạn. |
| **FR-ASG-03** | Điểm và nhận xét | Điểm AI hiện cho học viên ngay (D-113). Giáo viên, trợ giảng sửa điểm và viết nhận xét; mỗi lần sửa tạo bản ghi mới, giữ lịch sử, điểm cuối là bản mới nhất. Học viên nhận thông báo khi có điểm sửa hoặc nhận xét. |
| **FR-ASG-04** | Theo dõi bài được giao | Giáo viên xem theo từng bài: ai đã nộp, chưa nộp, nộp trễ, điểm. |

### B6. Mục 5 — thêm yêu cầu phi chức năng

> * **NFR-SEC-05:** Phân quyền theo lớp: mọi API của Phân hệ 11 kiểm tra người gọi đang được phân công lớp đó, hoặc có quyền giáo vụ; học viên chỉ đọc lớp mình đang học. Có test tự động chứng minh giáo viên lớp A không đọc, sửa được lớp B.
> * **NFR-SEC-06:** Dữ liệu người dưới 18 tuổi: lưu bằng chứng chấp thuận (thời điểm, phiên bản điều khoản, thông tin chấp thuận) theo BQ-17; tuân thủ Nghị định 13/2023 (pháp chế xác nhận).
> * **NFR-SEC-07:** Học tuần tự được kiểm ở máy chủ: nội dung chưa mở không trả video (kể cả link ký sẵn) hay câu hỏi.
> * **NFR-PERF-04:** Bài được giao có hạn chung cho cả lớp: hàng đợi chấm AI xử lý được lượt nộp dồn của cả lớp trước hạn; mục tiêu 50 lớp × 30 học viên nộp bài viết/nói cùng hạn, mọi bài có điểm trong 30 phút sau khi nộp (D-124); có load test chứng minh. Liên quan Q-25 (năng lực máy chấm nói).

### B7. Mục 6 — thêm thực thể

> * `programs`: chương trình, thang điểm, quy đổi (D-105).
> * `classes`, `class_staff`, `class_members`: lớp, phân công giáo viên/trợ giảng, học viên của lớp.
> * `class_sessions`, `attendance_marks`: buổi học (nhóm/1-1, online/offline) và điểm danh.
> * `assignments`, `assignment_targets`, `grade_overrides`: bài được giao, người nhận, điểm giáo viên sửa.
> * `unlock_overrides`: unit được mở sớm.
> * `courses` thêm chương trình, loại khóa, cài đặt học tuần tự; `user_profiles` thêm ngày sinh và bằng chứng chấp thuận điều khoản.
>
> Chi tiết: `docs/ba/lms-model.md`.

### B8. Mục 7 — thêm dòng truy vết

| Phân hệ | Nội dung | Mã | Ưu tiên | Giai đoạn |
|---|---|---|---|---|
| Phân hệ 3 | Học tuần tự, mở sớm | FR-LMS-04, FR-CLS-06 | High | Web khóa tự học |
| Phân hệ 9 | Nhiều bài kiểm tra, quy đổi theo chương trình | FR-EXAM-02, 03 | High | Web khóa tự học |
| Phân hệ 11 | Lớp, buổi học, điểm danh, giao bài, báo cáo | FR-CLS-01..07, FR-SES-01..04, FR-ASG-01..04 | High | Web khóa có lớp |

---

## Phần C. Mâu thuẫn cần dọn khi chép vào PRD/SRS

| Chỗ | Mâu thuẫn | Đề xuất |
|---|---|---|
| SRS 1.2, PRD 2.2 | "6 khóa IELTS", "không bán bản offline" | Thay bằng nhiều chương trình (D-105). Giữ "không bán bản offline": khóa bán online, một số buổi của lớp có thể học offline (D-106, D-124) |
| SRS 1.2 | Mobile phát triển song song, ra mắt một lượt | Thay bằng D-123 |
| SRS 2.2, D-24, D-37, BQ-08 | Quyền giáo viên ghi "chờ BQ-08" | Thay bằng D-99, D-108, D-109 |
| PRD 3 | "MVP chỉ bán khóa IELTS nên tập trung Nhóm 2" | Bỏ câu này hoặc chờ BQ-20 |
| FR-GOAL-02, DTO | Band IELTS 4.0–8.0 cố định | Theo chương trình (D-105) |
| `products` (V5) | Còn loại COMBO dù D-03 bỏ combo | Dọn khi thêm sản phẩm khóa có lớp |
| `docs/system-design.md` | Ghi danh theo level, role cố định STUDENT/ADMIN | Cập nhật theo code (theo khóa) và RBAC |

## Phần D. Câu hỏi chặn từng yêu cầu

| Câu hỏi | Chặn |
|---|---|
| BQ-17 cách phụ huynh chấp thuận, nội dung điều khoản | FR-AUTH-01, NFR-SEC-06 |
| BQ-18 đặc tả luyện tập mới | Luyện tập mới (D-116), chưa có FR |
| BQ-19 bài được giao có tính lượt AI không | FR-AI-04, FR-ASG-02 |
| BQ-20 chương trình làm trước, thang điểm | FR-GOAL-02, FR-TEST, FR-EXAM-03 |
| Phòng Đào tạo xác nhận unit = chương = Chủ đề (D-124) | FR-LMS-04, FR-EXAM-02 |

Các chỗ cần xác nhận trước đây đã trả lời ở D-124 (2026-11-19).
