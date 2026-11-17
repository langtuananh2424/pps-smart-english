package com.pps.smartenglish.exercise.controller;

import com.pps.smartenglish.exercise.dto.GrammarElementInfo;
import com.pps.smartenglish.exercise.service.GrammarElementService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** D-69: danh sách yếu tố ngữ pháp (tên, màu nhãn) cho màn làm bài; cần đăng nhập. */
@RestController
@RequestMapping("/api/grammar-elements")
class GrammarElementController {

    private final GrammarElementService service;

    GrammarElementController(GrammarElementService service) {
        this.service = service;
    }

    @GetMapping
    List<GrammarElementInfo> list() {
        return service.list();
    }
}
