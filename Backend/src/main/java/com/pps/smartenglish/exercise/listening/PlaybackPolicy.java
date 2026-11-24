package com.pps.smartenglish.exercise.listening;

import com.pps.smartenglish.exercise.domain.QuestionBank;
import com.pps.smartenglish.exercise.listening.ListeningDtos.RuleView;
import java.util.function.Supplier;

/**
 * Chiến lược phát audio theo chế độ của bộ (D-135). Máy chủ chỉ đếm và giới hạn được số lần cấp link; việc khóa tua, tự phát
 * nằm ở trình phát. Chấp nhận (chủ dự án): người cố ý vẫn tải được file qua link đã cấp, không chặn tuyệt đối.
 */
interface PlaybackPolicy {

    String mode();

    /** Số lần nghe tối đa mỗi part trong một lượt làm; null = không đếm (nghe tự do). */
    Integer limit(QuestionBank bank, Supplier<RuleView> rule);

    /** Nghe lại chỉ khi lần trước đã hết (Nghe ép). */
    boolean replayNeedsFinish();
}
