package com.pps.smartenglish.exercise.service;

import com.pps.smartenglish.catalog.repository.CourseRepository;
import com.pps.smartenglish.exercise.domain.QuestionBank;
import com.pps.smartenglish.exercise.dto.BankInfo;
import com.pps.smartenglish.exercise.repository.QuestionBankRepository;
import com.pps.smartenglish.lms.service.UnitUnlockService;
import com.pps.smartenglish.shared.error.ApiException;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/** Cổng chung: 404 nếu bank không tồn tại, 403 nếu không policy nào cho làm. */
@Component
class BankGate {

    private final QuestionBankRepository banks;
    private final List<BankAccessPolicy> policies;
    private final UnitUnlockService unitUnlock;
    private final CourseRepository courses;
    private final AssignedBanks assigned;

    BankGate(QuestionBankRepository banks, List<BankAccessPolicy> policies, UnitUnlockService unitUnlock, CourseRepository courses,
             AssignedBanks assigned) {
        this.banks = banks;
        this.policies = policies;
        this.unitUnlock = unitUnlock;
        this.courses = courses;
        this.assigned = assigned;
    }

    BankInfo require(Long bankId, Long userId) {
        return require(bankId, userId, false);
    }

    /**
     * inAssignment: người gọi đã kiểm họ là người nhận bài giao đúng câu này (FR-ASG-01): bỏ khóa unit tuần tự cho câu đó,
     * các policy quyền vẫn áp. Không mở cả bộ: chỗ khác của bộ vẫn gọi require thường.
     */
    BankInfo require(Long bankId, Long userId, boolean inAssignment) {
        var bank = banks.findInfo(bankId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "bank_not_found", "Không tìm thấy bộ câu hỏi"));
        if (policies.stream().noneMatch(p -> p.allows(userId, bank))) {
            throw new ApiException(HttpStatus.FORBIDDEN, "bank_locked", "Bạn chưa có quyền làm bộ câu hỏi này");
        }
        // Học tuần tự (FR-LMS-04); bài thi cuối khóa mở theo tỷ lệ học (ExamService), không theo thứ tự unit.
        if (courses.findByExamBankId(bankId).isEmpty() && !inAssignment && !assigned.isAssigned(userId, bankId)) {
            unitUnlock.requireUnlocked(userId, bank.topicId());
        }
        return bank;
    }

    /** D-82: Viết/Nói luyện tập không nhận câu của bộ thi khóa (làm cùng luồng thi). */
    BankInfo requirePracticable(Long bankId, Long userId) {
        return requirePracticable(bankId, userId, false);
    }

    /** inAttempt: lượt đã được AttemptLinks kiểm (của chính người gọi, đang mở, câu thuộc bộ của lượt): làm cùng luồng thi, không chặn EXAM (D-82). */
    BankInfo requireFor(Long bankId, Long userId, boolean inAssignment, boolean inAttempt) {
        return inAttempt ? require(bankId, userId, inAssignment) : requirePracticable(bankId, userId, inAssignment);
    }

    BankInfo requirePracticable(Long bankId, Long userId, boolean inAssignment) {
        // Giữ mode của bộ: luyện riêng không gắn lượt (có lượt đã đi qua requireFor/inAttempt, D-151)
        // Kiểm mode trước quyền: bộ thi chưa mở sẽ bị bank_locked che mất lý do thật.
        if (banks.findInfo(bankId).filter(b -> QuestionBank.EXAM.equals(b.mode())).isPresent()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "exam_question_not_practicable", "Câu này thuộc bài thi, không luyện tập riêng được");
        }
        return require(bankId, userId, inAssignment);
    }
}
