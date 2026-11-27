package com.pps.smartenglish.exercise.service;

import com.pps.smartenglish.exercise.domain.QuestionType;
import com.pps.smartenglish.shared.error.ApiException;
import java.util.Arrays;
import java.util.List;

/** D-152: dạng bài để lọc Luyện thêm. Dạng viết lại (AI chấm theo bộ) và dạng phụ thuộc nhóm (D-154) không nằm trong Luyện thêm. */
enum ExtraKind {
    AUTO, WRITING, SPEAKING;

    static ExtraKind of(QuestionType t) {
        return t == QuestionType.WRITING ? WRITING : t == QuestionType.SPEAKING ? SPEAKING : AUTO;
    }

    /** Dạng câu được dùng: loại câu viết lại. */
    static boolean usable(QuestionType t) {
        return !ResultAssembler.REWRITE_TYPES.contains(t) && !t.groupBound();
    }

    List<String> types() {
        return Arrays.stream(QuestionType.values()).filter(t -> usable(t) && of(t) == this).map(Enum::name).toList();
    }

    static List<String> allTypes() {
        return Arrays.stream(QuestionType.values()).filter(ExtraKind::usable).map(Enum::name).toList();
    }

    static ExtraKind parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return valueOf(raw);
        } catch (IllegalArgumentException e) {
            throw ApiException.validation("kind", "Chỉ nhận AUTO, WRITING hoặc SPEAKING");
        }
    }
}
