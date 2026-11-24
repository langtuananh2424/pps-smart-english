package com.pps.smartenglish.exercise.bulk;

import com.pps.smartenglish.exercise.domain.QuestionType;
import com.pps.smartenglish.exercise.dto.CmsQuestionDtos.CreateQuestion;
import java.util.List;

/**
 * Một dạng câu hỏi nhập được (question-editor-spec 5.3): cột Excel, dòng mẫu và cách đổi một dòng thành CreateQuestion.
 * Kết quả đi tiếp qua đúng bộ luật của soạn tay (CmsQuestionService.prepare), template không tự kiểm đáp án.
 */
public interface ImportTemplate {

    QuestionType type();

    /** Tên sheet Excel, đồng thời là tiêu đề "# ..." của dán nhanh. */
    String label();

    /** Cột của sheet mẫu theo thứ tự; người dùng có thể thêm cột đánh số (Lựa chọn K, Vế trái 7...) nếu dạng cho phép. */
    List<String> columns();

    /** Các dòng ví dụ điền sẵn trong file mẫu (cùng thứ tự cột); phải là dòng hợp lệ. */
    List<List<String>> samples();

    /** Ném RowException khi ô sai cú pháp; lỗi nội dung (đáp án, giới hạn) do validator chung báo. */
    CreateQuestion toRequest(RowCells row);

    /** Mô tả ngắn từng cột cho tài liệu hướng dẫn. */
    List<String> columnNotes();
}
