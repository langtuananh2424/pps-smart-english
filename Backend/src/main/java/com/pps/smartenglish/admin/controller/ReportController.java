package com.pps.smartenglish.admin.controller;

import com.pps.smartenglish.admin.dto.DashboardResponse;
import com.pps.smartenglish.admin.service.DashboardService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** FR-ADM-03: chỉ ADMIN (SecurityConfig chặn /api/admin/** theo role). */
@RestController
@RequestMapping("/api/admin/reports")
class ReportController {

    private final DashboardService dashboard;

    ReportController(DashboardService dashboard) {
        this.dashboard = dashboard;
    }

    /** from, to nhận chuỗi để sai định dạng trả 400 theo envelope chung thay vì lỗi chuyển kiểu của Spring. */
    @GetMapping("/dashboard")
    DashboardResponse dashboard(@RequestParam(required = false) String from, @RequestParam(required = false) String to) {
        return dashboard.dashboard(from, to);
    }
}
