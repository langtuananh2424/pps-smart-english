package com.pps.smartenglish.exercise.validation;

import com.fasterxml.jackson.databind.JsonNode;
import com.pps.smartenglish.exercise.domain.QuestionType;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Component;

/** Sắp xếp chữ cái: payload {letters:[{id,ch}]} (2..30 chữ, ch đúng một ký tự, id không lặp); key {word} gồm đúng các chữ đó. */
@Component
class LetterOrderValidator implements QuestionValidator {

    private static final int MAX_LETTERS = 30;

    @Override
    public QuestionType type() {
        return QuestionType.LETTER_ORDER;
    }

    @Override
    public void validate(QuestionDraft d, Set<String> grammarCodes) {
        JsonNode payload = JsonRules.object(d.payload(), "payload", Set.of("letters"));
        JsonNode letters = JsonRules.array(payload, "letters", "payload", 2);
        if (letters.size() > MAX_LETTERS) {
            throw JsonRules.bad("payload.letters", "Tối đa " + MAX_LETTERS + " chữ cái");
        }
        Set<String> ids = new HashSet<>();
        List<String> chars = new ArrayList<>();
        for (int i = 0; i < letters.size(); i++) {
            String at = "payload.letters[" + i + "]";
            JsonNode l = JsonRules.object(letters.get(i), at, Set.of("id", "ch"));
            if (!ids.add(JsonRules.text(l, "id", at, 20))) {
                throw JsonRules.bad(at + ".id", "Bị lặp");
            }
            String ch = JsonRules.text(l, "ch", at);
            if (ch.codePointCount(0, ch.length()) != 1) {
                throw JsonRules.bad(at + ".ch", "Phải đúng một ký tự");
            }
            chars.add(ch.toLowerCase(Locale.ROOT));
        }
        String word = JsonRules.text(JsonRules.object(d.answerKey(), "answerKey", Set.of("word")), "word", "answerKey", JsonRules.TEXT_MAX);
        List<String> wordChars = new ArrayList<>(word.toLowerCase(Locale.ROOT).codePoints().mapToObj(Character::toString).toList());
        Collections.sort(chars);
        Collections.sort(wordChars);
        if (!chars.equals(wordChars)) {
            throw JsonRules.bad("answerKey.word", "Phải gồm đúng các chữ cái trong payload.letters");
        }
    }
}
