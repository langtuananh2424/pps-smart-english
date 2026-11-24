package com.pps.smartenglish.exercise.listening;

import com.pps.smartenglish.exercise.domain.QuestionBank;
import com.pps.smartenglish.exercise.listening.ListeningDtos.RuleView;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/** Tra chiến lược theo playback_mode; giá trị lạ (không thể có do CHECK ở DB) coi như FREE để không chặn học viên. */
@Component
class PlaybackPolicies {

    private final Map<String, PlaybackPolicy> byMode;

    PlaybackPolicies(List<PlaybackPolicy> policies) {
        this.byMode = policies.stream().collect(Collectors.toMap(PlaybackPolicy::mode, Function.identity()));
    }

    PlaybackPolicy of(String mode) {
        return byMode.getOrDefault(mode, byMode.get(QuestionBank.FREE));
    }

    /** Nghe và tua tự do (chế độ luyện tập): không đếm, không giới hạn. */
    @Component
    static class Free implements PlaybackPolicy {
        public String mode() { return QuestionBank.FREE; }
        public Integer limit(QuestionBank bank, Supplier<RuleView> rule) { return null; }
        public boolean replayNeedsFinish() { return false; }
    }

    /** Chế độ Thi: tự phát, số lần nghe do người tạo đề đặt trên bộ (mặc định cấu hình). */
    @Component
    static class Exam implements PlaybackPolicy {

        private final ListeningProperties props;

        Exam(ListeningProperties props) {
            this.props = props;
        }

        public String mode() { return QuestionBank.EXAM; }

        public Integer limit(QuestionBank bank, Supplier<RuleView> rule) {
            return bank.getPlayLimit() != null ? bank.getPlayLimit() : props.examPlayLimit();
        }

        public boolean replayNeedsFinish() { return false; }
    }

    /** Nghe ép: số lần nghe theo luật; chỉ nghe lại khi audio đã hết. */
    @Component
    static class Locked implements PlaybackPolicy {
        public String mode() { return QuestionBank.LOCKED; }
        public Integer limit(QuestionBank bank, Supplier<RuleView> rule) { return rule.get().playLimit(); }
        public boolean replayNeedsFinish() { return true; }
    }
}
