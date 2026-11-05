package com.pps.smartenglish.exercise.service;

import com.pps.smartenglish.exercise.domain.QuestionType;
import com.pps.smartenglish.exercise.dto.BankResponse;
import com.pps.smartenglish.exercise.dto.BankResponse.ChoiceView;
import com.pps.smartenglish.exercise.dto.BankResponse.QuestionView;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** FR-EX-01, FR-EX-02: đề bài cho học viên; chỉ chuyển các trường an toàn sang DTO. */
@Service
public class BankService {

    private final BankGate gate;
    private final QuestionLoader loader;

    BankService(BankGate gate, QuestionLoader loader) {
        this.gate = gate;
        this.loader = loader;
    }

    @Transactional(readOnly = true)
    public BankResponse get(Long bankId, Long userId) {
        var bank = gate.require(bankId, userId);
        var set = loader.load(bankId);
        var views = set.questions().stream().map(q -> new QuestionView(q.getId(), q.getType().name(), q.getPrompt(),
                q.getPayload(),
                q.getType() == QuestionType.CONNECTIVE_MCQ
                        ? set.choicesOf(q).stream().map(c -> new ChoiceView(c.getId(), c.getLabel(), c.getContent())).toList()
                        : null)).toList();
        return new BankResponse(bank.id(), bank.title(), bank.lessonId(), bank.trial(), views);
    }
}
