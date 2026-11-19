# Góp ý thiết kế Figma: nhóm thanh toán và lớp học (bản gửi designer)

Ngày 2026-11-19 · Phạm vi: page "Design" của file "PPS App bán lẻ", các nhóm "13 · Thanh toán và đơn hàng", "Học viên · Lớp và lịch học", "Học viên · Bài được giao", "Giáo viên · …", "Giáo vụ · …".

Mức: **P0** sửa trước khi giao FE · **P1** nên sửa · **P2** tinh chỉnh.
Đã xem: 6 màn thanh toán, 9 màn lớp học (chủ yếu desktop). Chưa xem hết bản mobile và các màn Xác nhận nộp, Đổi/hủy lịch, Xếp lớp bị từ chối, Đóng lớp, Rút học viên.

## A. Chung cho mọi màn

| # | Mức | Góp ý |
|---|---|---|
| A1 | P0 | Dữ liệu mẫu còn là chữ mô tả ("Tên do giáo vụ đặt", "Giờ · Giáo viên", "Học viên 1"). Thêm một bản có dữ liệu đầy đủ (tên, ngày giờ, số liệu, chữ dài) để kiểm tra cắt chữ và hàng dài. |
| A2 | P1 | Logo thanh bên không thống nhất: màn thanh toán dùng "PPS", màn lớp học dùng "PPS SMART ENGLISH" và chữ tràn ra mép, đè lên icon "Trang chủ". Chọn một bản. |
| A3 | P1 | Thanh đầu trang (chip XP, chuỗi ngày, chuông) có ở màn lớp học, không có ở màn thanh toán. Thống nhất. |
| A4 | P1 | Nút lọc dạng tab ("Cần làm / Đã nộp", "Tất cả / Chỉ hiện tụt lại", "Đang mở / Đã đóng") đang dùng icon tick và mũi tên xuống, trông như ô chọn hoặc dropdown. Dùng segmented control hoặc tab chuẩn của Design System. |
| A5 | P2 | Chiều cao khung không đều (873 px ở Tiến độ lớp, 820 px ở các màn khác). |

## B. Thanh toán

| # | Mức | Màn | Góp ý |
|---|---|---|---|
| B1 | P0 | Thanh toán VietQR (desktop, mobile) | Khối "Thử các trạng thái trong prototype" nằm trong màn. Đưa ra ngoài khung hoặc ghi chú chỉ để demo. Trên mobile khối này bị thanh tab dưới che. |
| B2 | P0 | Xác nhận đơn hàng | Thiếu bước chọn thời hạn (D-127: admin cấu hình nhiều mức thời hạn cho từng khóa). Thêm chọn thời hạn, giá theo mức, ngày hết hạn quyền học. Giá và thời hạn là dữ liệu thật, số ví dụ lấy từ `docs/ba/decisions.md` mục 2; giá từng mức đang chờ BQ-01, tạm để placeholder rõ ràng. |
| B3 | P0 | Xác nhận đơn hàng, VietQR | Bỏ chữ "Giá minh họa dùng để duyệt giao diện", "Gói học mẫu", "Mã minh họa". |
| B4 | P0 | Thanh toán VietQR | Thiếu: đồng hồ đếm ngược hết hạn, tên ngân hàng, số tài khoản, nút sao chép nội dung chuyển khoản, chỉ báo "đang chờ xác nhận". Hiện chỉ có dòng "hết hạn theo thời gian hiển thị trên đơn" nhưng màn không hiển thị thời gian. |
| B5 | P1 | Xác nhận đơn hàng | Thẻ "Tổng đơn hàng" có đường kẻ thừa ở trên, dưới và bên phải. Cột trái chỉ có một thẻ nhỏ rồi để trống. |
| B6 | P1 | Thanh toán VietQR | Thẻ bên phải không có tiêu đề, nền cam lộ ra giữa hai thẻ. |
| B7 | P1 | Luồng thanh toán | Thanh bên: màn Xác nhận không tô mục nào, các màn khác tô "Hồ sơ". Chọn một mục dùng cho cả luồng. |
| B8 | P1 | Danh sách đơn | Thêm cột ngày mua và hạn khóa. Đơn "Hết hạn" chỉ có "Xem chi tiết", thêm "Tạo đơn mới". Thêm trạng thái "Thất bại / Đã hủy". |
| B9 | P1 | Chi tiết đơn | Chỉ có 4 dòng. Thêm ngày mua, hạn khóa, mã giao dịch, tên khóa và thời hạn đã mua. |
| B10 | P1 | VietQR, Chi tiết đơn | Thiếu lối "Đã chuyển khoản nhưng đơn chưa lên", dẫn sang hỗ trợ. |
| B11 | P2 | Đặt tên frame | Bỏ mã đơn demo khỏi tên frame ("Đơn PPS-DEMO-002 · …"), đặt theo trạng thái. |

## C. Học viên, lớp và bài được giao

| # | Mức | Màn | Góp ý |
|---|---|---|---|
| C1 | P0 | Lớp của tôi | Thẻ Chuyên cần không có số liệu (số buổi có mặt, vắng, tỷ lệ). Thẻ "Buổi kế tiếp" và "Bài được giao" chỉ có một dòng mô tả: thêm buổi kế tiếp (ngày giờ, giáo viên, hình thức) và số bài cần làm, hạn gần nhất. |
| C2 | P0 | Lịch buổi học | Các nút T2 đến CN không có ngày, không có nút chuyển tuần. Buổi offline thiếu địa điểm. Mỗi buổi cần nút riêng (vào lớp online, xem địa điểm). Hiện chỉ có một nút "Vào lớp" chung. |
| C3 | P1 | Lịch buổi học | Dòng "Đã hủy" cần khác biệt rõ (gạch ngang, chip). Nhãn "Theo lịch" và "Sắp diễn ra" dễ nhầm, chốt bộ trạng thái buổi học. |
| C4 | P1 | Bài được giao · Cần làm | "Đã qua hạn" đang là chữ thường: dùng chip đỏ. Thêm hạn nộp cụ thể (ngày giờ). Mỗi dòng một thao tác thay cho một nút "Mở bài" chung. |
| C5 | P1 | Bài được giao | Thiếu trạng thái "Nộp trễ" (FR-CLS-05 dùng để đánh dấu tụt lại). |
| C6 | P1 | Lớp của tôi | Thiếu trạng thái lớp đã đóng và học viên đã bị rút khỏi lớp. |
| C7 | P2 | Kết quả bài được giao | Màn tốt: "Điểm cuối" và "Điểm AI ban đầu" tách rõ. Chỉ cần thay "Giáo viên của lớp" bằng tên người chấm. |

## D. Giáo viên, trợ giảng

| # | Mức | Màn | Góp ý |
|---|---|---|---|
| D1 | P0 | Tiến độ lớp | Khối đỏ "Học viên tụt lại" chỉ ghi chung "Lý do theo ngưỡng cấu hình". Cần liệt kê từng học viên kèm chip lý do (quá hạn chưa nộp, vắng nhiều, chậm hơn lớp, điểm thấp). Dòng tụt lại đang chỉ có "· Cần hỗ trợ" gắn vào tên. |
| D2 | P1 | Tiến độ lớp | Hiện tên lớp đang xem hoặc có bộ chọn lớp. Tiến độ dùng thanh tiến độ thay vì chỉ số %. Thêm sắp xếp. |
| D3 | P0 | Điểm danh | Thêm tiêu đề buổi (lớp, ngày, giờ, hình thức). Hàng học viên dùng tên thật. |
| D4 | P1 | Điểm danh | Ô chưa chọn đang có icon mũi tên xuống như dropdown, dùng radio hoặc nút chọn. Thêm ghi chú riêng cho từng học viên, thêm bộ đếm (đã đánh dấu x/y). |
| D5 | P1 | Chấm bài | Bài làm học viên cần vùng đủ cho bài dài (số từ, giờ nộp, nộp trễ). Speaking cần trình phát audio. Thêm nút bài nộp trước/sau. |
| D6 | P1 | Chấm bài | Ô "Điểm mới" ghi "Theo thang chương trình": cần thang điểm cụ thể (chờ BQ-20). Bỏ icon cuốn sổ lặp ở mọi ô nhập. |

## E. Giáo vụ

| # | Mức | Màn | Góp ý |
|---|---|---|---|
| E1 | P1 | Quản lý lớp học | Bảng thiếu cột: khóa, giáo viên, lịch học, thời gian lớp, tiến độ. Thêm tìm kiếm và lọc theo khóa. |
| E2 | P1 | Quản lý lớp học | Cột Sĩ số lúc "18/25" lúc "Đủ sĩ số": luôn hiện "x/y", thêm chip "Đầy" nếu cần. |
| E3 | P1 | Quản lý lớp học | Nút "Tạo lớp" và "Xem chi tiết" nằm lửng dưới bảng. "Tạo lớp" đặt ở đầu trang, "Xem chi tiết" gắn vào từng dòng. |
| E4 | P2 | Quản lý lớp học | Tên "Lớp của IELTS Foundation" nghe lạ, dùng tên lớp thật theo quy ước giáo vụ. |

## Việc cần chủ dự án quyết định trước khi designer sửa

- B2: các mức thời hạn hiển thị trên màn mua, giá từng mức (BQ-01).
- D6: thang điểm chấm bài được giao (BQ-20).
- C3: bộ trạng thái chuẩn của buổi học (sắp diễn ra, đã đổi, đã hủy, theo lịch).
