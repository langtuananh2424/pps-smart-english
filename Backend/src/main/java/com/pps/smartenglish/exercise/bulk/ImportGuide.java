package com.pps.smartenglish.exercise.bulk;

import org.springframework.stereotype.Component;

/**
 * Tài liệu markdown hướng dẫn nhờ AI (ChatGPT, Claude...) đổi đề Word sang Excel đúng mẫu (D-133). Phần cột sinh từ
 * template nên không bao giờ lệch với file mẫu. Trả qua endpoint thay vì file tĩnh để luôn khớp bản đang chạy.
 */
@Component
public class ImportGuide {

    private final TemplateRegistry templates;
    private final ImportProperties props;

    ImportGuide(TemplateRegistry templates, ImportProperties props) {
        this.templates = templates;
        this.props = props;
    }

    public String markdown() {
        StringBuilder md = new StringBuilder("""
                # Đổi đề Word sang Excel bằng AI

                Soạn đề trên Word rồi nhờ một công cụ AI (ChatGPT, Claude...) sắp lại thành bảng đúng mẫu, sau đó tải bảng lên mục
                "Nhập hàng loạt". Hệ thống luôn cho xem trước và báo lỗi từng dòng, chưa ghi gì cho tới khi bạn xác nhận; nhập nhầm
                thì hoàn tác được cả lô.

                ## Cách làm

                1. Tải file mẫu Excel của dạng câu hỏi cần nhập (mỗi dạng một sheet).
                2. Mở công cụ AI, dán nội dung đề từ Word kèm lời nhắn mẫu bên dưới.
                3. Nhờ AI trả bảng, chép vào đúng sheet của file mẫu (giữ nguyên dòng tiêu đề), lưu .xlsx.
                4. Tải lên, xem trước, sửa lỗi, rồi xác nhận.

                ## Lời nhắn mẫu cho AI

                > Hãy chuyển đề dưới đây thành bảng để dán vào Excel, mỗi câu một dòng, đúng các cột và quy ước ở phần "Cột từng dạng"
                > mà tôi gửi kèm. Chỉ trả bảng (các cột ngăn bằng tab), không giải thích thêm. Ô nào không có dữ liệu thì để trống.
                > Không bịa đáp án: câu nào đề không nêu đáp án thì để trống ô đáp án để tôi tự điền.

                ## Cột từng dạng

                """);
        md.insert(md.indexOf("## Cột từng dạng"), "Giới hạn mỗi lần nhập: không quá " + props.maxRows() + " câu, file tối đa "
                + props.maxFileBytes() / 1024 + " KB (có thể chia nhỏ đề).\n\n");
        for (ImportTemplate t : templates.all()) {
            md.append("### ").append(t.label()).append("\n\n").append("Cột: ").append(String.join(" | ", t.columns())).append("\n\n");
            t.columnNotes().forEach(n -> md.append("- ").append(n).append('\n'));
            md.append("\nVí dụ (các ô ngăn bằng ` ; `): ");
            md.append(String.join(" ; ", t.samples().get(0))).append("\n\n");
        }
        md.append("""
                ## Lưu ý

                - Đánh số vị trí từ (cột Cụm, Vị trí từ sai) bắt đầu từ 1.
                - Ô có thẻ HTML được làm sạch như khi soạn tay; ảnh, bảng, âm thanh nhập trực tiếp trên trình soạn, không qua Excel.
                - Nhập từ file Word trực tiếp chưa hỗ trợ.
                """);
        return md.toString();
    }
}
