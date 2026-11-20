package com.pps.smartenglish.classroom.controller;

import com.pps.smartenglish.classroom.dto.ClassDtos.ClassDetail;
import com.pps.smartenglish.classroom.dto.ClassDtos.ClassView;
import com.pps.smartenglish.classroom.service.ClassReadService;
import com.pps.smartenglish.shared.security.CurrentUser;
import com.pps.smartenglish.shared.web.PathId;
import java.util.List;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/** FR-CLS-01..04 phía giáo viên, trợ giảng, học viên: chỉ đọc lớp của chính mình (NFR-SEC-05). */
@RestController
class ClassController {

    private final ClassReadService service;

    ClassController(ClassReadService service) {
        this.service = service;
    }

    @GetMapping("/api/teaching/classes")
    List<ClassView> teaching(Authentication auth) {
        return service.teaching(CurrentUser.id(auth));
    }

    @GetMapping("/api/me/classes")
    List<ClassView> mine(Authentication auth) {
        return service.attending(CurrentUser.id(auth));
    }

    @GetMapping("/api/classes/{id}")
    ClassDetail get(Authentication auth, @PathVariable String id) {
        return service.detail(CurrentUser.id(auth), PathId.parse(id, "class_not_found", "Không tìm thấy lớp"));
    }
}
