package com.pps.smartenglish.support.service;

import com.pps.smartenglish.shared.error.ApiException;
import com.pps.smartenglish.shared.ratelimit.RateLimiter;
import com.pps.smartenglish.support.domain.SupportTicket;
import com.pps.smartenglish.support.dto.TicketDtos.CreateTicket;
import com.pps.smartenglish.support.dto.TicketDtos.CreatedTicket;
import com.pps.smartenglish.support.dto.TicketDtos.Info;
import com.pps.smartenglish.support.dto.TicketDtos.MessageView;
import com.pps.smartenglish.support.dto.TicketDtos.TicketDetail;
import com.pps.smartenglish.support.dto.TicketDtos.TicketView;
import com.pps.smartenglish.support.repository.SupportTicketRepository;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * FR-SUP-02, phía học viên: tạo ticket, nhắn tiếp, xem, đóng. Chỉ học viên đã đăng nhập (BQ-09), mỗi học viên có tối đa
 * một ticket chưa đóng để nhắn tiếp trong đó thay vì mở nhiều ticket. Ticket của người khác luôn là 404.
 */
@Service
public class TicketService {

    static final int LIST_LIMIT = 20;
    private static final int MESSAGES_PER_MINUTE = 20;

    private final SupportTicketRepository tickets;
    private final TicketMessenger messenger;
    private final SupportSettingsService settings;
    private final RateLimiter limiter;
    private final Clock clock;

    TicketService(SupportTicketRepository tickets, TicketMessenger messenger, SupportSettingsService settings, RateLimiter limiter,
                  Clock clock) {
        this.tickets = tickets;
        this.messenger = messenger;
        this.settings = settings;
        this.limiter = limiter;
        this.clock = clock;
    }

    @Transactional
    public CreatedTicket create(long userId, CreateTicket req) {
        limit(userId);
        tickets.findFirstByUserIdAndStatusNotOrderByIdDesc(userId, SupportTicket.CLOSED).ifPresent(active -> {
            throw new ApiException(HttpStatus.CONFLICT, "ticket_active", "Bạn đang có ticket chưa đóng, hãy nhắn tiếp trong đó",
                    Map.of("ticketId", active.getId()), null);
        });
        var ticket = tickets.saveAndFlush(new SupportTicket(userId, req.subject().strip(), clock.instant()));
        messenger.add(ticket.getId(), userId, false, req.message());
        return new CreatedTicket(view(tickets.findById(ticket.getId()).orElseThrow()), settings.info());
    }

    @Transactional(readOnly = true)
    public List<TicketView> mine(long userId) {
        return tickets.findByUserIdOrderByIdDesc(userId, PageRequest.of(0, LIST_LIMIT)).stream().map(TicketService::view).toList();
    }

    /** after = id tin nhắn cuối giao diện đã có; trả các tin mới hơn (hỏi lại định kỳ để thấy tin nhân viên). */
    @Transactional(readOnly = true)
    public TicketDetail get(long userId, long ticketId, Long after) {
        var ticket = own(userId, ticketId);
        return new TicketDetail(view(ticket), messenger.after(ticketId, after));
    }

    @Transactional
    public MessageView send(long userId, long ticketId, String body) {
        var ticket = own(userId, ticketId);
        limit(userId);
        if (SupportTicket.CLOSED.equals(ticket.getStatus())) {
            tickets.reopen(ticketId, clock.instant());
        }
        var m = messenger.add(ticketId, userId, false, body);
        return new MessageView(m.getId(), false, m.getBody(), m.getCreatedAt());
    }

    /** Idempotent. */
    @Transactional
    public TicketView close(long userId, long ticketId) {
        own(userId, ticketId);
        tickets.close(ticketId, clock.instant());
        return view(tickets.findById(ticketId).orElseThrow());
    }

    public Info info() {
        return settings.info();
    }

    private SupportTicket own(long userId, long ticketId) {
        return tickets.findByIdAndUserId(ticketId, userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "ticket_not_found", "Không tìm thấy ticket"));
    }

    private void limit(long userId) {
        limiter.requireHit("rl:ticket:msg:" + userId, MESSAGES_PER_MINUTE, Duration.ofMinutes(1), "ticket:msg:" + userId);
    }

    static TicketView view(SupportTicket t) {
        return new TicketView(t.getId(), t.getSubject(), t.getStatus(), t.getAssignedTo() != null, t.getLastMessageAt(), t.getCreatedAt());
    }
}
