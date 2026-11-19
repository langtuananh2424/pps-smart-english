package com.pps.smartenglish.support.controller;

import com.pps.smartenglish.shared.security.CurrentUser;
import com.pps.smartenglish.shared.web.CursorPage;
import com.pps.smartenglish.shared.web.PathId;
import com.pps.smartenglish.support.dto.TicketDtos.MessageView;
import com.pps.smartenglish.support.dto.TicketDtos.SendMessage;
import com.pps.smartenglish.support.dto.TicketDtos.SettingsView;
import com.pps.smartenglish.support.dto.TicketDtos.StaffTicketDetail;
import com.pps.smartenglish.support.dto.TicketDtos.StaffTicketRow;
import com.pps.smartenglish.support.dto.TicketDtos.UpdateSettings;
import com.pps.smartenglish.support.service.SupportSettingsService;
import com.pps.smartenglish.support.service.TicketStaffService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** FR-SUP-02 phía nhân viên: ticket cần ticket.manage; sửa cấu hình cần support.configure (SecurityConfig). */
@RestController
@RequestMapping("/api/admin/support")
class AdminTicketController {

    private static final String NOT_FOUND = "ticket_not_found";
    private static final String NOT_FOUND_MSG = "Không tìm thấy ticket";

    private final TicketStaffService tickets;
    private final SupportSettingsService settings;

    AdminTicketController(TicketStaffService tickets, SupportSettingsService settings) {
        this.tickets = tickets;
        this.settings = settings;
    }

    @GetMapping("/tickets")
    CursorPage<StaffTicketRow> list(@RequestParam(required = false) String status, @RequestParam(required = false) String limit,
                                    @RequestParam(required = false) String cursor) {
        return tickets.list(status, limit, cursor);
    }

    @GetMapping("/tickets/{id}")
    StaffTicketDetail get(@PathVariable String id, @RequestParam(required = false) Long after) {
        return tickets.get(PathId.parse(id, NOT_FOUND, NOT_FOUND_MSG), after);
    }

    @PostMapping("/tickets/{id}/claim")
    StaffTicketRow claim(Authentication auth, @PathVariable String id) {
        return tickets.claim(CurrentUser.id(auth), PathId.parse(id, NOT_FOUND, NOT_FOUND_MSG));
    }

    @PostMapping("/tickets/{id}/messages")
    @ResponseStatus(HttpStatus.CREATED)
    MessageView reply(Authentication auth, @PathVariable String id, @Valid @RequestBody SendMessage req) {
        return tickets.reply(CurrentUser.id(auth), PathId.parse(id, NOT_FOUND, NOT_FOUND_MSG), req.body());
    }

    @PostMapping("/tickets/{id}/close")
    StaffTicketRow close(@PathVariable String id) {
        return tickets.close(PathId.parse(id, NOT_FOUND, NOT_FOUND_MSG));
    }

    @GetMapping("/settings")
    SettingsView settings() {
        return settings.get();
    }

    @PutMapping("/settings")
    SettingsView updateSettings(@Valid @RequestBody UpdateSettings req) {
        return settings.update(req);
    }
}
