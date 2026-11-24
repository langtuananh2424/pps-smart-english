package com.pps.smartenglish.exercise.listening;

import com.pps.smartenglish.exercise.listening.ListeningDtos.RuleView;
import java.util.Map;

/** Quy tắc đạt của Nghe ép (D-142): theo cả bài HOẶC theo part, không cả hai. Hàm thuần để dùng cho kết quả và bảng tiến độ giáo viên. */
final class ForcedPass {

    /** Số câu đúng và số câu của một part. */
    record Part(int correct, int total) {}

    private ForcedPass() {}

    /** Theo part: mọi part có ngưỡng đều phải đạt, bỏ qua ngưỡng cả bài. Theo cả bài: chỉ xét tổng. */
    static boolean passed(RuleView rule, int correct, int total, Map<Long, Part> parts) {
        if (!rule.byPart()) {
            return atLeast(correct, total, rule.passPctTotal());
        }
        return rule.groupThresholds().stream().allMatch(t -> {
            Part p = parts.get(t.groupId());
            return p == null || atLeast(p.correct(), p.total(), t.passPct());
        });
    }

    /** correct/total >= pct/100 bằng số nguyên, không làm tròn nên 69,9% không bị tính thành 70%. */
    static boolean atLeast(int correct, int total, int pct) {
        return total > 0 && (long) correct * 100 >= (long) pct * total;
    }
}
