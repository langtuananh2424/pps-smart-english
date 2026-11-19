package com.pps.smartenglish.support.controller;

import com.pps.smartenglish.shared.web.PathId;
import com.pps.smartenglish.support.dto.SupportDtos.ActivateResult;
import com.pps.smartenglish.support.dto.SupportDtos.BonusRequest;
import com.pps.smartenglish.support.dto.SupportDtos.BonusResult;
import com.pps.smartenglish.support.dto.SupportDtos.ReasonRequest;
import com.pps.smartenglish.support.dto.SupportDtos.StudentDetail;
import com.pps.smartenglish.support.dto.SupportDtos.StudentRow;
import com.pps.smartenglish.support.service.SupportService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** FR-SUP-03: GET cần student.view, POST cần student.support (SecurityConfig). */
@RestController
@RequestMapping("/api/admin/support")
class AdminSupportController {

    private static final String NOT_FOUND = "student_not_found";
    private static final String NOT_FOUND_MSG = "Không tìm thấy học viên";

    private final SupportService service;

    AdminSupportController(SupportService service) {
        this.service = service;
    }

    @GetMapping("/students")
    List<StudentRow> search(@RequestParam(required = false) String q) {
        return service.search(q);
    }

    @GetMapping("/students/{id}")
    StudentDetail detail(@PathVariable String id) {
        return service.detail(PathId.parse(id, NOT_FOUND, NOT_FOUND_MSG));
    }

    @PostMapping("/students/{id}/unlock")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void unlock(@PathVariable String id, @Valid @RequestBody ReasonRequest req) {
        service.unlock(PathId.parse(id, NOT_FOUND, NOT_FOUND_MSG), req.reason().strip());
    }

    @PostMapping("/students/{id}/bonus-quota")
    BonusResult bonus(@PathVariable String id, @Valid @RequestBody BonusRequest req) {
        return service.bonus(PathId.parse(id, NOT_FOUND, NOT_FOUND_MSG), req.amount(), req.reason().strip());
    }

    @PostMapping("/students/{id}/resend-verification")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void resendVerification(@PathVariable String id) {
        service.resendVerification(PathId.parse(id, NOT_FOUND, NOT_FOUND_MSG));
    }

    @PostMapping("/orders/{code}/activate")
    ActivateResult activate(@PathVariable String code, @Valid @RequestBody ReasonRequest req) {
        return service.activateOrder(code, req.reason().strip());
    }
}
