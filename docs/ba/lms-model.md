# Mô hình khóa học → lớp (đề xuất)

Ngày: 2026-11-19 · Trạng thái: đề xuất, chưa duyệt · Căn cứ: D-104..D-122 trong `decisions.md`. Chưa đưa vào PRD/SRS và `docs/system-design.md`; chưa code.

## 1. Hai loại khóa, một nền chung

```
Chương trình (IELTS, TOEIC…)  ── thang điểm, quy đổi, lộ trình
  └─ Khóa học  [loại: TỰ HỌC | CÓ LỚP]
       ├─ Nội dung chung cho cả hai loại
       │    ├─ Cây bài học: cấp độ → chủ đề/unit → bài giảng
       │    ├─ Bài tập sẵn (gắn bài giảng)
       │    ├─ Luyện tập thêm
       │    └─ Kiểm tra: theo chương + cuối khóa
       └─ Chỉ khóa CÓ LỚP
            └─ Lớp (ngày bắt đầu, kết thúc, online/offline, sĩ số)
                 ├─ Giáo viên, trợ giảng (kiêm gia sư 1-1)
                 ├─ Học viên của lớp
                 ├─ Buổi học: nhóm hoặc kèm 1-1, online (link) / offline (giờ)
                 │    └─ Điểm danh
                 └─ Bài được giao (trỏ tới nội dung có sẵn của khóa)
                      └─ Bài làm, điểm AI, giáo viên sửa điểm và nhận xét
```

- Khóa tự học dùng đúng phần nội dung chung; khóa có lớp thêm tầng lớp. Không cần tách hệ thống về sau (D-117).
- Ghi danh khóa (`user_course_enrollments`) giữ nguyên, là quyền học phần tự học. Thành viên lớp là bản ghi riêng, theo lịch lớp (D-110).

## 2. Dữ liệu đề xuất

Thay đổi trên bảng có sẵn (migration mới từ V11, tương thích ngược):

| Bảng | Thay đổi | Lý do |
|---|---|---|
| `programs` (mới) | mã, tên, cấu hình thang điểm, quy đổi | D-105 thay `courses.target_type` và band IELTS cố định |
| `courses` | thêm `program_id`, `delivery_mode` SELF_PACED / CLASS_BASED | D-117 |
| `question_banks` | thêm loại kiểm tra theo chương; bỏ ràng buộc một bài thi mỗi khóa (`courses.exam_bank_id`) | D-118 |
| `student_exercise_attempts` | thêm `assignment_id` (nullable), `is_late` | Bài được giao, D-121 |
| `courses` | thêm `sequential` (bật/tắt học tuần tự) | D-122 |
| Tiến độ mở khóa | unit xong = xem hết video + làm xong bài tập (tính từ `lesson_progress` + attempts); kiểm tra chương phải làm, không cần đạt | D-119, D-122 |
| `unlock_overrides` (mới) | class_id, user_id (null = cả lớp), unit_id, granted_by | Giáo viên mở sớm (D-122) |

Bảng mới cho tầng lớp:

| Bảng | Cột chính | Ràng buộc |
|---|---|---|
| `classes` | course_id, tên, starts_on, ends_on, mode ONLINE/OFFLINE, capacity, status | Mỗi lớp có ngày bắt đầu riêng, không có bảng đợt (D-120) |
| `class_staff` | class_id, user_id, role TEACHER/ASSISTANT, assigned_from, assigned_to | Một TEACHER đang phụ trách mỗi lớp (unique index có điều kiện) |
| `class_members` | class_id, user_id, enrollment_id, status ACTIVE/TRANSFERRED/WITHDRAWN/COMPLETED | Một bản ghi ACTIVE mỗi (lớp, học viên); một lớp ACTIVE mỗi (khóa, học viên) |
| `class_sessions` | class_id, kind GROUP/TUTORING, student_id (khi 1-1), mode, starts_at, ends_at, meeting_url, taught_by, status | Link chỉ cho buổi online (D-106) |
| `attendance_marks` | session_id, user_id, status PRESENT/ABSENT/LATE/EXCUSED, marked_by, marked_at | Một bản ghi mỗi (buổi, học viên) |
| `assignments` | class_id, nội dung (bank hoặc bài giảng), due_at, created_by, status | Unique (lớp, nội dung, hạn) khi ACTIVE, chống giao trùng |
| `assignment_targets` | assignment_id, user_id | Không có dòng nào = cả lớp |
| `grade_overrides` | attempt_id, score, comment, graded_by, is_final | Giáo viên sửa điểm bằng bản ghi mới, giữ lịch sử (D-113) |

Lượt AI: thêm nguồn `ASSIGNMENT` theo lớp, không trừ lượt tuần (D-115). Báo cáo số buổi dạy: đếm `class_sessions` đã điểm danh theo `taught_by` (D-111).

## 3. Phân quyền theo lớp

- Một hàm dùng chung `ClassAccessGuard.requireStaff(classId)` cho mọi API của tầng lớp: người gọi phải là `class_staff` đang phụ trách lớp; giáo vụ (quyền `class.manage`) được bỏ qua.
- Học viên chỉ đọc lớp mình là `class_members` ACTIVE.
- Có test chứng minh giáo viên lớp A không đọc được lớp B (mục P0 checklist production).

## 4. Học từ pps-education

| Lấy | Không lấy |
|---|---|
| Phân công giáo viên có `assigned_from/to` và unique index một giáo viên chính đang phụ trách | Site, điểm trường, `site_teachers`, data scope theo site |
| Unique một ghi danh ACTIVE mỗi (lớp, học sinh) | Phòng học, kiểm tra trùng phòng, tiết học, năm học, kỳ |
| Giao bài: hạn nộp, danh sách học sinh nhận, chống trùng bằng unique index | Giao bài thông qua Nhận xét (UC-21) |
| Chấm sửa bằng bản ghi mới có `is_final` | Lớp liên kết đối tác, CRM, check-in GPS của giáo viên |
| Thông báo khi giao bài, nhắc hạn nộp | Kiểm tra quyền lớp rải rác từng service: gom về một guard |

## 5. Còn mở

BQ-17..BQ-20 trong `decisions.md`.
