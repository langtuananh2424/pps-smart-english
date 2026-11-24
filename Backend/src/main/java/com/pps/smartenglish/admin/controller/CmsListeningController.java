package com.pps.smartenglish.admin.controller;

import com.pps.smartenglish.exercise.listening.ForcedRules;
import com.pps.smartenglish.exercise.listening.ListeningDtos.RuleInput;
import com.pps.smartenglish.exercise.listening.ListeningDtos.RuleView;
import com.pps.smartenglish.shared.web.PathId;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** FR-EX-10, D-136, D-138: người tạo đề đặt luật Nghe ép mặc định của bộ (bộ phải ở playbackMode LOCKED). Quyền cms.content. */
@RestController
@RequestMapping("/api/admin/cms/banks/{id}/forced-listening")
class CmsListeningController {

    private final ForcedRules rules;

    CmsListeningController(ForcedRules rules) {
        this.rules = rules;
    }

    @GetMapping
    RuleView get(@PathVariable String id) {
        return rules.cmsView(bankId(id));
    }

    @PutMapping
    RuleView put(@PathVariable String id, @Valid @RequestBody RuleInput req) {
        return rules.upsertBank(bankId(id), req);
    }

    private static Long bankId(String raw) {
        return PathId.parse(raw, "bank_not_found", "Không tìm thấy bộ câu hỏi");
    }
}
