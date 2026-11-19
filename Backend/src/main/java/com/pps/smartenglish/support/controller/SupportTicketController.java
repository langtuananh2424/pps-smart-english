package com.pps.smartenglish.support.controller;

import com.pps.smartenglish.shared.security.CurrentUser;
import com.pps.smartenglish.shared.web.PathId;
import com.pps.smartenglish.support.dto.TicketDtos.CreateTicket;
import com.pps.smartenglish.support.dto.TicketDtos.CreatedTicket;
import com.pps.smartenglish.support.dto.TicketDtos.Info;
import com.pps.smartenglish.support.dto.TicketDtos.MessageView;
import com.pps.smartenglish.support.dto.TicketDtos.SendMessage;
import com.pps.smartenglish.support.dto.TicketDtos.TicketDetail;
import com.pps.smartenglish.support.dto.TicketDtos.TicketView;
import com.pps.smartenglish.support.service.TicketService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** FR-SUP-02, phía học viên đã đăng nhập (BQ-09): chỉ thấy ticket của chính mình. */
@RestController
@RequestMapping("/api/support")
class SupportTicketController {

    private static final String NOT_FOUND = "ticket_not_found";
    private static final String NOT_FOUND_MSG = "Không tìm thấy ticket";

    private final TicketService service;

    SupportTicketController(TicketService service) {
        this.service = service;
    }

    @GetMapping("/info")
    Info info() {
        return service.info();
    }

    @PostMapping("/tickets")
    @ResponseStatus(HttpStatus.CREATED)
    CreatedTicket create(Authentication auth, @Valid @RequestBody CreateTicket req) {
        return service.create(CurrentUser.id(auth), req);
    }

    @GetMapping("/tickets")
    List<TicketView> mine(Authentication auth) {
        return service.mine(CurrentUser.id(auth));
    }

    @GetMapping("/tickets/{id}")
    TicketDetail get(Authentication auth, @PathVariable String id, @RequestParam(required = false) Long after) {
        return service.get(CurrentUser.id(auth), PathId.parse(id, NOT_FOUND, NOT_FOUND_MSG), after);
    }

    @PostMapping("/tickets/{id}/messages")
    @ResponseStatus(HttpStatus.CREATED)
    MessageView send(Authentication auth, @PathVariable String id, @Valid @RequestBody SendMessage req) {
        return service.send(CurrentUser.id(auth), PathId.parse(id, NOT_FOUND, NOT_FOUND_MSG), req.body());
    }

    @PostMapping("/tickets/{id}/close")
    TicketView close(Authentication auth, @PathVariable String id) {
        return service.close(CurrentUser.id(auth), PathId.parse(id, NOT_FOUND, NOT_FOUND_MSG));
    }
}
