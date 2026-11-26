package com.pps.smartenglish.exercise.service;

import com.pps.smartenglish.catalog.domain.TopicSkill;
import com.pps.smartenglish.shared.error.ApiException;
import com.pps.smartenglish.exercise.domain.QuestionBank;
import com.pps.smartenglish.exercise.domain.StudentAttempt;
import com.pps.smartenglish.exercise.repository.QuestionBankRepository;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * D-151, FR-EX-05: chế độ của lượt. Bộ IELTS Reading/Listening (bộ khóa, mặc định PRACTICE, topic READING/LISTENING) cho học viên
 * chọn Luyện tập hoặc Mô phỏng thi khi bắt đầu; bộ khác giữ chế độ của bộ.
 */
@Component
public class AttemptModes {

    private static final Set<TopicSkill> IELTS_SKILLS = Set.of(TopicSkill.READING, TopicSkill.LISTENING);

    private final QuestionBankRepository banks;

    AttemptModes(QuestionBankRepository banks) {
        this.banks = banks;
    }

    /** Bộ cho chọn chế độ: bộ khóa PRACTICE có kỹ năng IELTS. Bộ thi khóa (EXAM) và đề đầu vào không thuộc phạm vi. */
    public boolean choosable(QuestionBank bank) {
        return QuestionBank.COURSE.equals(bank.getPurpose()) && QuestionBank.PRACTICE.equals(bank.getMode())
                && banks.findSkill(bank.getId()).filter(IELTS_SKILLS::contains).isPresent();
    }

    /** Chế độ cho lượt mới; 400 khi giá trị lạ hoặc bộ ngoài phạm vi mà đòi chế độ khác chế độ của bộ. */
    String resolve(QuestionBank bank, String requested) {
        if (requested == null) {
            return bank.getMode();
        }
        if (!QuestionBank.PRACTICE.equals(requested) && !QuestionBank.EXAM.equals(requested)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "attempt_mode_invalid", "Chế độ chỉ nhận PRACTICE hoặc EXAM");
        }
        if (!requested.equals(bank.getMode()) && !choosable(bank)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "attempt_mode_not_allowed", "Bộ này không cho chọn chế độ làm bài");
        }
        return requested;
    }

    /** Chế độ hiệu lực của lượt: đã lưu, hoặc theo bộ cho lượt cũ. */
    public String of(StudentAttempt a) {
        return a.getMode() != null ? a.getMode()
                : banks.findById(a.getBankId()).map(QuestionBank::getMode).orElse(QuestionBank.EXAM);
    }
}
