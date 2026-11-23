# Product

<!-- impeccable:product-schema 1 -->

## Platform

web

## Users

PPS Smart English phục vụ người tự học tiếng Anh theo nhiều mục tiêu. **IELTS là trọng tâm hiện tại**, đặc biệt với người cần luyện cách triển khai ý, dùng từ nối và làm quen với bài thi trên máy. Các nhóm khác gồm người luyện Cambridge, học sinh lớp 6–12 và sinh viên hoặc người đi làm cần củng cố nền tảng hoặc luyện tiếng Anh ứng dụng.

## Product Purpose

PPS Smart English là nền tảng B2C để người học tự tìm khóa phù hợp, học thử, mua khóa và luyện theo tiến độ cá nhân. Người học có thể nối tiếp bài giảng bằng bài tập, hiểu vì sao câu trả lời đúng hoặc sai, rồi dùng phản hồi để luyện tiếp.

## Positioning

Phương pháp PPS đưa từ kết nối vào video theo ngữ cảnh và giúp người học vận dụng tám yếu tố ngữ pháp vào bài viết IELTS. Trợ lý viết đặt câu hỏi để người học tự phát triển ý, sau đó dùng ý của họ để hỗ trợ dựng bản nháp và góp ý. Cách luyện thi trên máy giúp người học làm quen với thao tác đọc đề, điều hướng câu hỏi, rà soát và nộp bài.

## Operating Context

- Luồng học viên đi từ chọn mục tiêu, tìm và học thử khóa đến xem bài giảng, làm bài tập, nhận phản hồi và theo dõi tiến độ.
- Giai đoạn 1 phục vụ trình duyệt desktop và mobile. Ứng dụng Flutter thuộc giai đoạn sau.
- Trang **Design** trong Figma là bản thiết kế chính. Các màn luyện tập PPS và mô phỏng IELTS được đặt tên riêng để phân biệt mục đích sử dụng.

## Capabilities and Constraints

- PRD và SRS là nguồn yêu cầu chức năng. Các mẫu Figma dùng để duyệt giao diện và prototype. Tính năng, cấu hình chấm và điều kiện đạt cần đối chiếu với tài liệu dự án trước khi triển khai.
- Trong thiết kế IELTS, bố cục làm bài theo tham chiếu IDP để học viên tập thao tác trên máy. Luồng luyện tập có hỗ trợ từ Cam.
- Với bài video phản xạ, kết quả đạt dựa trên cấu hình và kết quả chấm do backend trả về. Cơ chế giám sát vẫn là quyết định mở.

## Brand Commitments

- Tên sản phẩm là **PPS Smart English**. Cam là linh vật và trợ lý học tập. Dùng bộ tài sản Cam đã duyệt trong Figma, giữ nhận diện gốc khi bổ sung tư thế hoặc chuyển động.
- Cam nói bằng tiếng Việt ngắn gọn, thân thiện. Khi học viên làm sai, Cam chỉ ra lý do hoặc gợi ý một bước để học viên thử lại. Nội dung luyện tiếng Anh giữ theo đề bài.

## Evidence on Hand

- [PRD](docs/PRD.md) và [SRS](docs/SRS.md) ghi phạm vi, hành trình, chức năng và yêu cầu phi chức năng.
- [Figma · Design](https://www.figma.com/design/CYuJRLBrMAXMwlLe43uZyy/PPS-App-b%C3%A1n-l%E1%BA%BB?node-id=1-2) có mẫu desktop, mobile, Reading ngang và prototype bài tập. [Figma · Design System](https://www.figma.com/design/CYuJRLBrMAXMwlLe43uZyy/PPS-App-b%C3%A1n-l%E1%BA%BB?node-id=0-1) có tài sản nhận diện và linh vật Cam.

## Product Principles

1. Giữ hệ mục tiêu linh hoạt cho nhiều nhóm người học và ưu tiên các luồng phục vụ IELTS khi cần chọn phạm vi.
2. Giúp học viên tự suy luận, phát triển ý và sửa bài dựa trên phản hồi cụ thể.
3. Giữ nội dung câu hỏi, hướng dẫn và thao tác chính dễ tìm trong suốt phiên làm bài.
4. Dùng phản hồi và động viên ở những thời điểm có ý nghĩa, như sau khi kiểm tra đáp án hoặc hoàn thành bài.

## Accessibility & Inclusion

Theo SRS §5.5, giao diện đáp ứng từ 375 px đến 1920 px trở lên, với màu sắc và độ tương phản theo WCAG 2.1 AA để phục vụ các phiên học kéo dài 30–45 phút.
