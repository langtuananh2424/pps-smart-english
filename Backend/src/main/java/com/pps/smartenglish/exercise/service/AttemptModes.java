package com.pps.smartenglish.exercise.service;

import com.pps.smartenglish.exercise.domain.QuestionBank;
import com.pps.smartenglish.exercise.domain.StudentAttempt;
import com.pps.smartenglish.exercise.repository.QuestionBankRepository;
import org.springframework.stereotype.Component;

/**
 * D-155, FR-EX-05: chế độ của lượt do người soạn và giáo viên đặt, học viên không chọn. Lượt thuộc bài giao có chế độ thì theo bài giao,
 * không thì theo question_banks.mode. Chế độ lưu trên lượt (D-151 mục 3) nên đổi bộ hay bài giao sau đó không đổi lượt đang làm.
 */
@Component
public class AttemptModes {

    private final QuestionBankRepository banks;
    private final AssignedBanks assigned;

    AttemptModes(QuestionBankRepository banks, AssignedBanks assigned) {
        this.banks = banks;
        this.assigned = assigned;
    }

    /** Chế độ cho lượt mới: chế độ của bài giao (nếu có) thắng chế độ của bộ. */
    String resolve(QuestionBank bank, String assignmentMode) {
        return assignmentMode != null ? assignmentMode : bank.getMode();
    }

    /** Học viên là người nhận bài giao Thi trên bộ này: GET bộ không được lộ gợi ý trước khi bắt đầu lượt. */
    public boolean examAssigned(long userId, long bankId) {
        return assigned.isExamAssigned(userId, bankId);
    }

    /**
     * Lượt này được kiểm câu/xem gợi ý: chế độ Luyện tập, và (D-162) lượt tự học không bị bộ đang giao Thi chặn.
     * Lượt tự học mở trước khi giao vẫn còn, chỉ mất gợi ý.
     */
    public boolean practiceAllowed(StudentAttempt a) {
        return QuestionBank.PRACTICE.equals(of(a)) && !(a.getAssignmentId() == null && examAssigned(a.getUserId(), a.getBankId()));
    }

    /** Chế độ hiệu lực của lượt: đã lưu, hoặc theo bộ cho lượt cũ. */
    public String of(StudentAttempt a) {
        return a.getMode() != null ? a.getMode()
                : banks.findById(a.getBankId()).map(QuestionBank::getMode).orElse(QuestionBank.EXAM);
    }
}
