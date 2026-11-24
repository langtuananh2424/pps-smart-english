package com.pps.smartenglish.exercise.bulk;

import com.pps.smartenglish.exercise.domain.GrammarElement;
import com.pps.smartenglish.exercise.domain.QuestionBank;
import com.pps.smartenglish.exercise.dto.CmsQuestionDtos.CreateQuestion;
import com.pps.smartenglish.exercise.dto.ImportDtos.RowError;
import com.pps.smartenglish.exercise.service.CmsQuestionService;
import com.pps.smartenglish.exercise.service.CmsQuestionService.Prepared;
import com.pps.smartenglish.shared.error.ApiException;
import jakarta.validation.Validator;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * Kiểm một câu nhập bằng đúng bộ luật của soạn tay: ràng buộc DTO (độ dài, số lựa chọn...) rồi CmsQuestionService.prepare
 * (làm sạch HTML, validator theo dạng, thẻ ngữ pháp). Dùng chung cho xem trước và xác nhận nên hai bước không thể lệch nhau.
 */
@Component
class RowChecker {

    /** prepared null khi có lỗi. */
    record Checked(Prepared prepared, List<RowError> errors) {}

    private final Validator validator;
    private final CmsQuestionService cms;

    RowChecker(Validator validator, CmsQuestionService cms) {
        this.validator = validator;
        this.cms = cms;
    }

    Checked check(QuestionBank bank, CreateQuestion req, Map<String, GrammarElement> elements) {
        List<RowError> errors = new ArrayList<>();
        validator.validate(req).forEach(v -> errors.add(new RowError(v.getPropertyPath().toString(), v.getMessage())));
        if (!errors.isEmpty()) {
            return new Checked(null, errors);
        }
        try {
            return new Checked(cms.prepare(bank, req, elements), List.of());
        } catch (ApiException e) {
            if (e.getStatus() != HttpStatus.BAD_REQUEST) {
                throw e; // 404/409... là lỗi của yêu cầu, không phải của dòng
            }
            e.getDetails().forEach((field, message) -> errors.add(new RowError(field, String.valueOf(message))));
            if (errors.isEmpty()) {
                errors.add(new RowError("question", e.getMessage()));
            }
            return new Checked(null, errors);
        }
    }
}
