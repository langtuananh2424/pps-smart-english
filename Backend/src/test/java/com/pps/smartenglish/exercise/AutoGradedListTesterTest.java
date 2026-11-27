package com.pps.smartenglish.exercise;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.pps.smartenglish.exercise.domain.QuestionType;
import com.pps.smartenglish.exercise.grading.GraderRegistry;
import com.pps.smartenglish.support.ApiTestBase;
import java.util.Arrays;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** Tester D-164: danh sách dạng không tự chấm ở Web (admin/cms/questions/types.ts NOT_AUTO_GRADED) phải khớp GraderRegistry. */
class AutoGradedListTesterTest extends ApiTestBase {

    // Bản sao của NOT_AUTO_GRADED phía Web; đổi một bên thì đổi cả hai.
    private static final Set<String> WEB_NOT_AUTO = Set.of("WRITING", "SPEAKING", "CONNECTIVE_REWRITE", "GRAMMAR_REWRITE", "LISTEN_RECORD",
            "VIDEO_SHORT", "VIDEO_CUE", "VIDEO_DISCUSS", "VIDEO_PICTURE");

    @Autowired private GraderRegistry graders;

    @Test
    void webNotAutoGradedListMatchesBackend() {
        var backend = Arrays.stream(QuestionType.values()).filter(t -> !graders.isAutoGraded(t)).map(Enum::name)
                .collect(Collectors.toCollection(TreeSet::new));
        assertEquals(new TreeSet<>(WEB_NOT_AUTO), backend);
    }
}
