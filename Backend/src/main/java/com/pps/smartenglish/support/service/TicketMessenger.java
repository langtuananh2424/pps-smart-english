package com.pps.smartenglish.support.service;

import com.pps.smartenglish.support.domain.SupportMessage;
import com.pps.smartenglish.support.dto.TicketDtos.MessageView;
import com.pps.smartenglish.support.repository.SupportMessageRepository;
import com.pps.smartenglish.support.repository.SupportTicketRepository;
import java.time.Clock;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

/** Ghi và đọc tin nhắn của ticket; dùng chung cho phía học viên và nhân viên. Gọi trong transaction của người gọi. */
@Component
class TicketMessenger {

    static final int PAGE = 100;

    private final SupportMessageRepository messages;
    private final SupportTicketRepository tickets;
    private final Clock clock;

    TicketMessenger(SupportMessageRepository messages, SupportTicketRepository tickets, Clock clock) {
        this.messages = messages;
        this.tickets = tickets;
        this.clock = clock;
    }

    SupportMessage add(long ticketId, long senderId, boolean fromStaff, String body) {
        var saved = messages.saveAndFlush(new SupportMessage(ticketId, senderId, fromStaff, body.strip()));
        tickets.touch(ticketId, clock.instant());
        return saved;
    }

    /** Tin có id lớn hơn after, tăng dần, tối đa PAGE tin mỗi lần. */
    List<MessageView> after(long ticketId, Long after) {
        return messages.findByTicketIdAndIdGreaterThanOrderByIdAsc(ticketId, after == null ? 0L : after, PageRequest.of(0, PAGE))
                .stream().map(m -> new MessageView(m.getId(), m.isFromStaff(), m.getBody(), m.getCreatedAt())).toList();
    }
}
