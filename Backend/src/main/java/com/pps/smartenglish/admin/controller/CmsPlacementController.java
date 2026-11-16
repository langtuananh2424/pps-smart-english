package com.pps.smartenglish.admin.controller;

import com.pps.smartenglish.exercise.dto.CmsQuestionDtos.BankView;
import com.pps.smartenglish.exercise.dto.CmsQuestionDtos.CreatePlacementBank;
import com.pps.smartenglish.exercise.service.CmsBankService;
import com.pps.smartenglish.placement.dto.PlacementDtos.BandRuleView;
import com.pps.smartenglish.placement.dto.PlacementDtos.ConfigUpdate;
import com.pps.smartenglish.placement.dto.PlacementDtos.ConfigView;
import com.pps.smartenglish.placement.dto.PlacementDtos.ReplaceBandRules;
import com.pps.smartenglish.placement.service.CmsPlacementService;
import com.pps.smartenglish.shared.web.PathId;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * FR-ADM-06, FR-TEST-01: cấu hình kiểm tra đầu vào. Đề là một bộ câu hỏi PLACEMENT: xem, sửa, thêm câu hỏi dùng chung
 * /banks/{id} và /banks/{id}/questions của CmsQuestionController; riêng xuất bản đi qua đây (chỉ một đề hoạt động).
 */
@RestController
@RequestMapping("/api/admin/cms/placement")
class CmsPlacementController {

    private final CmsBankService banks;
    private final CmsPlacementService placement;

    CmsPlacementController(CmsBankService banks, CmsPlacementService placement) {
        this.banks = banks;
        this.placement = placement;
    }

    @GetMapping("/banks")
    List<BankView> list() {
        return banks.listPlacement();
    }

    @PostMapping("/banks")
    @ResponseStatus(HttpStatus.CREATED)
    BankView create(@Valid @RequestBody CreatePlacementBank req) {
        return banks.createPlacement(req);
    }

    @PostMapping("/banks/{id}/publish")
    BankView publish(@PathVariable String id) {
        return placement.publish(id(id));
    }

    @PostMapping("/banks/{id}/unpublish")
    BankView unpublish(@PathVariable String id) {
        return placement.unpublish(id(id));
    }

    @GetMapping("/config")
    ConfigView config() {
        return placement.config();
    }

    @PutMapping("/config")
    ConfigView updateConfig(@Valid @RequestBody ConfigUpdate req) {
        return placement.updateConfig(req.maxRetakes());
    }

    @GetMapping("/band-rules")
    List<BandRuleView> bandRules() {
        return placement.bandRules();
    }

    @PutMapping("/band-rules")
    List<BandRuleView> replaceBandRules(@Valid @RequestBody ReplaceBandRules req) {
        return placement.replaceBandRules(req.rules());
    }

    private static long id(String raw) {
        return PathId.parse(raw, "bank_not_found", "Không tìm thấy đề kiểm tra đầu vào");
    }
}
