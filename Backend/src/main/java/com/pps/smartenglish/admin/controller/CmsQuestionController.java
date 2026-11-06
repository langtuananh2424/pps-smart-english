package com.pps.smartenglish.admin.controller;

import com.pps.smartenglish.exercise.dto.CmsQuestionDtos.BankDetail;
import com.pps.smartenglish.exercise.dto.CmsQuestionDtos.BankView;
import com.pps.smartenglish.exercise.dto.CmsQuestionDtos.CreateBank;
import com.pps.smartenglish.exercise.dto.CmsQuestionDtos.CreateQuestion;
import com.pps.smartenglish.exercise.dto.CmsQuestionDtos.GrammarElementView;
import com.pps.smartenglish.exercise.dto.CmsQuestionDtos.PatchBank;
import com.pps.smartenglish.exercise.dto.CmsQuestionDtos.PatchQuestion;
import com.pps.smartenglish.exercise.dto.CmsQuestionDtos.QuestionDetail;
import com.pps.smartenglish.exercise.service.CmsBankService;
import com.pps.smartenglish.exercise.service.CmsQuestionService;
import com.pps.smartenglish.shared.web.PathId;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * FR-ADM-02: soạn bộ câu hỏi và câu hỏi. Cho ADMIN và EDITOR (SecurityConfig). Ghi qua service của module exercise.
 * Phản hồi ở đây có answerKey và isCorrect nên tuyệt đối không dùng chung DTO với đường học viên.
 */
@RestController
@RequestMapping("/api/admin/cms")
class CmsQuestionController {

    private final CmsBankService banks;
    private final CmsQuestionService questions;

    CmsQuestionController(CmsBankService banks, CmsQuestionService questions) {
        this.banks = banks;
        this.questions = questions;
    }

    @GetMapping("/topics/{id}/banks")
    List<BankView> listBanks(@PathVariable String id) {
        return banks.list(id(id, "topic_not_found"));
    }

    @PostMapping("/topics/{id}/banks")
    @ResponseStatus(HttpStatus.CREATED)
    BankView createBank(@PathVariable String id, @Valid @RequestBody CreateBank req) {
        return banks.create(id(id, "topic_not_found"), req);
    }

    @GetMapping("/banks/{id}")
    BankDetail bank(@PathVariable String id) {
        return banks.get(id(id, "bank_not_found"));
    }

    @PatchMapping("/banks/{id}")
    BankView patchBank(@PathVariable String id, @Valid @RequestBody PatchBank req) {
        return banks.patch(id(id, "bank_not_found"), req);
    }

    @DeleteMapping("/banks/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void deleteBank(@PathVariable String id) {
        banks.delete(id(id, "bank_not_found"));
    }

    @PostMapping("/banks/{id}/publish")
    BankView publishBank(@PathVariable String id) {
        return banks.setPublished(id(id, "bank_not_found"), true);
    }

    @PostMapping("/banks/{id}/unpublish")
    BankView unpublishBank(@PathVariable String id) {
        return banks.setPublished(id(id, "bank_not_found"), false);
    }

    @PostMapping("/banks/{id}/questions")
    @ResponseStatus(HttpStatus.CREATED)
    QuestionDetail createQuestion(@PathVariable String id, @Valid @RequestBody CreateQuestion req) {
        return questions.create(id(id, "bank_not_found"), req);
    }

    @PatchMapping("/questions/{id}")
    QuestionDetail patchQuestion(@PathVariable String id, @Valid @RequestBody PatchQuestion req) {
        return questions.patch(id(id, "question_not_found"), req);
    }

    @DeleteMapping("/questions/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void deleteQuestion(@PathVariable String id) {
        questions.delete(id(id, "question_not_found"));
    }

    /** Danh mục 8 yếu tố có sẵn để gắn thẻ; không có CRUD. */
    @GetMapping("/grammar-elements")
    List<GrammarElementView> grammarElements() {
        return banks.grammarElements();
    }

    private static long id(String raw, String notFoundCode) {
        return PathId.parse(raw, notFoundCode, "Không tìm thấy");
    }
}
