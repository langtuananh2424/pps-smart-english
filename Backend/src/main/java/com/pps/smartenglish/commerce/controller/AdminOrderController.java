package com.pps.smartenglish.commerce.controller;

import com.pps.smartenglish.commerce.dto.AdminOrderDetail;
import com.pps.smartenglish.commerce.dto.AdminOrderView;
import com.pps.smartenglish.commerce.service.AdminOrderService;
import com.pps.smartenglish.shared.web.CursorPage;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** FR-ADM-03, D-37/D-38: quyền order.view (SecurityConfig). */
@RestController
@RequestMapping("/api/admin/orders")
class AdminOrderController {

    private final AdminOrderService service;

    AdminOrderController(AdminOrderService service) {
        this.service = service;
    }

    @GetMapping
    CursorPage<AdminOrderView> list(@RequestParam(required = false) String status,
                                    @RequestParam(required = false) String from,
                                    @RequestParam(required = false) String to,
                                    @RequestParam(required = false) String email,
                                    @RequestParam(required = false) String limit,
                                    @RequestParam(required = false) String cursor) {
        return service.search(status, from, to, email, limit, cursor);
    }

    @GetMapping("/{id}")
    AdminOrderDetail detail(@PathVariable long id) {
        return service.detail(id);
    }

    /** Ghi thẳng ra response (không buffer toàn bộ); kiểm tra tham số xong mới ghi header. */
    @GetMapping("/export.csv")
    void exportCsv(@RequestParam(required = false) String from, @RequestParam(required = false) String to,
                   HttpServletResponse res) throws IOException {
        var range = service.prepareExport(from, to);
        res.setContentType("text/csv;charset=UTF-8");
        res.setHeader("Content-Disposition", "attachment; filename=\"orders_" + range.from() + "_" + range.to() + ".csv\"");
        service.writeCsv(range, res.getOutputStream());
    }
}
