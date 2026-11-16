package com.pps.smartenglish.admin.controller;

import com.pps.smartenglish.exercise.dto.CamDtos.FaqInput;
import com.pps.smartenglish.exercise.dto.CamDtos.FaqView;
import com.pps.smartenglish.exercise.dto.CamDtos.HintInput;
import com.pps.smartenglish.exercise.dto.CamDtos.HintView;
import com.pps.smartenglish.exercise.service.CmsFaqService;
import com.pps.smartenglish.exercise.service.CmsHintService;
import com.pps.smartenglish.shared.web.PathId;
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

/** FR-AI-06, FR-AI-07 (D-28): soạn gợi ý và hỏi đáp cho Cam. Quyền cms.content áp ở SecurityConfig. */
@RestController
@RequestMapping("/api/admin/cms")
class CmsCamController {

    private final CmsHintService hints;
    private final CmsFaqService faqs;

    CmsCamController(CmsHintService hints, CmsFaqService faqs) {
        this.hints = hints;
        this.faqs = faqs;
    }

    @GetMapping("/questions/{id}/hints")
    List<HintView> listHints(@PathVariable String id) {
        return hints.list(id(id, "question_not_found"));
    }

    @PostMapping("/questions/{id}/hints")
    @ResponseStatus(HttpStatus.CREATED)
    HintView createHint(@PathVariable String id, @RequestBody HintInput req) {
        return hints.create(id(id, "question_not_found"), req);
    }

    @PatchMapping("/hints/{id}")
    HintView patchHint(@PathVariable String id, @RequestBody HintInput req) {
        return hints.patch(id(id, "hint_not_found"), req);
    }

    @DeleteMapping("/hints/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void deleteHint(@PathVariable String id) {
        hints.delete(id(id, "hint_not_found"));
    }

    @GetMapping("/banks/{id}/faqs")
    List<FaqView> listFaqs(@PathVariable String id) {
        return faqs.list(id(id, "bank_not_found"));
    }

    @PostMapping("/banks/{id}/faqs")
    @ResponseStatus(HttpStatus.CREATED)
    FaqView createFaq(@PathVariable String id, @RequestBody FaqInput req) {
        return faqs.create(id(id, "bank_not_found"), req);
    }

    @PatchMapping("/faqs/{id}")
    FaqView patchFaq(@PathVariable String id, @RequestBody FaqInput req) {
        return faqs.patch(id(id, "faq_not_found"), req);
    }

    @DeleteMapping("/faqs/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void deleteFaq(@PathVariable String id) {
        faqs.delete(id(id, "faq_not_found"));
    }

    private static long id(String raw, String notFoundCode) {
        return PathId.parse(raw, notFoundCode, "Không tìm thấy");
    }
}
