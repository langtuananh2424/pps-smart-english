package com.pps.smartenglish.admin.controller;

import com.pps.smartenglish.exercise.dto.ExamBandDtos.ReplaceRules;
import com.pps.smartenglish.exercise.dto.ExamBandDtos.RuleView;
import com.pps.smartenglish.exercise.service.CmsExamBandService;
import com.pps.smartenglish.shared.web.PathId;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** FR-ADM-06, FR-EXAM-03, D-12: bảng quy đổi số câu đúng -> band của đề thi khóa (cms.content qua /api/admin/cms/**). */
@RestController
@RequestMapping("/api/admin/cms/banks/{id}/band-rules")
class CmsExamBandController {

    private final CmsExamBandService service;

    CmsExamBandController(CmsExamBandService service) {
        this.service = service;
    }

    @GetMapping
    List<RuleView> rules(@PathVariable String id) {
        return service.rules(id(id));
    }

    @PutMapping
    List<RuleView> replace(@PathVariable String id, @Valid @RequestBody ReplaceRules req) {
        return service.replace(id(id), req.rules());
    }

    private static long id(String raw) {
        return PathId.parse(raw, "bank_not_found", "Không tìm thấy đề thi khóa");
    }
}
