package com.pps.smartenglish.exercise.service;

import com.pps.smartenglish.exercise.domain.QuestionBank;
import com.pps.smartenglish.exercise.domain.StudentAttempt;
import com.pps.smartenglish.exercise.listening.ForcedRules;
import com.pps.smartenglish.exercise.repository.QuestionBankRepository;
import org.springframework.stereotype.Component;

/**
 * FR-EX-10: lượt làm của bộ ở chế độ Nghe ép (LOCKED) sao chép result_mode COUNT_ONLY và luật đang áp ngay lúc bắt đầu, nên đổi
 * chế độ hay luật của bộ sau đó không đổi cách trả kết quả của lượt đang làm.
 */
@Component
class ForcedAttempts {

    private final QuestionBankRepository banks;
    private final ForcedRules rules;

    ForcedAttempts(QuestionBankRepository banks, ForcedRules rules) {
        this.banks = banks;
        this.rules = rules;
    }

    /** Tự học: theo chế độ hiện tại của bộ lúc bắt đầu. */
    StudentAttempt applySelfStudy(StudentAttempt attempt) {
        boolean locked = banks.findById(attempt.getBankId()).map(b -> QuestionBank.LOCKED.equals(b.getPlaybackMode())).orElse(false);
        return locked ? attempt.countOnly(rules.ruleIdFor(attempt.getBankId(), null)) : attempt;
    }

    /**
     * Bài giao: theo cờ chốt lúc giao, không theo chế độ hiện tại của bộ. Bài giao Nghe ép mà bộ đã đổi về FREE thì lượt mới vẫn
     * COUNT_ONLY theo luật đã chụp của bài giao (nhất quán); bài giao thường mà bộ đổi sang LOCKED thì vẫn FULL.
     */
    StudentAttempt applyAssigned(StudentAttempt attempt, long assignmentId, boolean forcedAssignment) {
        return forcedAssignment ? attempt.countOnly(rules.ruleIdFor(attempt.getBankId(), assignmentId)) : attempt;
    }
}
