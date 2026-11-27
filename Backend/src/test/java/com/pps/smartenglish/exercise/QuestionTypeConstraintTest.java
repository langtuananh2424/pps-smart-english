package com.pps.smartenglish.exercise;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.pps.smartenglish.ai.AiTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

/** V28: DB nhận 5 kiểu và 2 kỹ năng mới, vẫn từ chối giá trị lạ. */
class QuestionTypeConstraintTest extends AiTestSupport {

    private void insertQuestion(long bank, String type, String skill) {
        jdbc.update("insert into questions (bank_id, type, prompt, sort_order, skill) values (?, ?, 'p', 0, ?)", bank, type, skill);
    }

    @Test
    void acceptsNewTypesAndSkills() {
        long bank = bank(true);
        for (String t : new String[] {"CHOICE_MULTI", "TRUE_FALSE", "FILL_TEXT", "WORD_BANK", "MATCH_OPTIONS", "CONNECTIVE_MCQ"}) {
            assertDoesNotThrow(() -> insertQuestion(bank, t, null), t);
        }
        for (String s : new String[] {"LISTENING", "READING", "GRAMMAR", "WRITING", "SPEAKING"}) {
            assertDoesNotThrow(() -> insertQuestion(bank, "FILL_TEXT", s), s);
        }
    }

    /** Mỗi giá trị enum phải qua CHECK: thêm kiểu vào QuestionType mà quên migration nới CHECK thì test này đỏ. */
    @Test
    void acceptsEveryQuestionType() {
        long bank = bank(true);
        for (var t : com.pps.smartenglish.exercise.domain.QuestionType.values()) {
            assertDoesNotThrow(() -> insertQuestion(bank, t.name(), null), t.name());
        }
    }

    @Test
    void rejectsUnknownTypeAndSkill() {
        long bank = bank(true);
        assertThrows(DataIntegrityViolationException.class, () -> insertQuestion(bank, "ESSAY", null));
        assertThrows(DataIntegrityViolationException.class, () -> insertQuestion(bank, "fill_text", null));
        assertThrows(DataIntegrityViolationException.class, () -> insertQuestion(bank, "FILL_TEXT", "MATH"));
    }
}
