package com.pps.smartenglish.support.service;

import com.pps.smartenglish.retention.service.NotificationService;
import com.pps.smartenglish.shared.audit.AuditRecorder;
import com.pps.smartenglish.shared.error.ApiException;
import com.pps.smartenglish.shared.web.CursorPage;
import com.pps.smartenglish.shared.web.Cursors;
import com.pps.smartenglish.support.dto.TicketDtos.MessageView;
import com.pps.smartenglish.support.dto.TicketDtos.StaffTicketDetail;
import com.pps.smartenglish.support.dto.TicketDtos.StaffTicketRow;
import com.pps.smartenglish.support.domain.SupportTicket;
import com.pps.smartenglish.support.repository.SupportTicketRepository;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * FR-SUP-02, phía nhân viên (quyền ticket.manage): xem hộp thư, nhận xử lý, trả lời, đóng. Một ticket chỉ một người xử lý
 * tại một thời điểm (BQ-09, trả lời tạm); nhận và trả lời đều là UPDATE có điều kiện nên không có hai người cùng giành.
 */
@Service
public class TicketStaffService {

    public static final int DEFAULT_LIMIT = 20;
    public static final int MAX_LIMIT = 100;

    private final SupportTicketRepository tickets;
    private final TicketMessenger messenger;
    private final NotificationService notifications;
    private final AuditRecorder audit;
    private final Clock clock;

    TicketStaffService(SupportTicketRepository tickets, TicketMessenger messenger, NotificationService notifications,
                       AuditRecorder audit, Clock clock) {
        this.tickets = tickets;
        this.messenger = messenger;
        this.notifications = notifications;
        this.audit = audit;
        this.clock = clock;
    }

    /** status rỗng/null = mọi trạng thái; sai giá trị thì 400. */
    @Transactional(readOnly = true)
    public CursorPage<StaffTicketRow> list(String status, String limit, String cursor) {
        String st = status == null ? "" : status.strip().toUpperCase();
        if (!st.isEmpty() && !List.of(SupportTicket.OPEN, SupportTicket.IN_PROGRESS, SupportTicket.CLOSED).contains(st)) {
            throw ApiException.validation("status", "Chỉ nhận OPEN, IN_PROGRESS hoặc CLOSED");
        }
        int size = Cursors.size(limit, DEFAULT_LIMIT, MAX_LIMIT);
        var rows = tickets.staffPage(st, Cursors.decode(cursor), size + 1).stream().map(TicketStaffService::row).toList();
        boolean more = rows.size() > size;
        var page = more ? rows.subList(0, size) : rows;
        return new CursorPage<>(page, more ? Cursors.encode(page.get(size - 1).id()) : null);
    }

    @Transactional(readOnly = true)
    public StaffTicketDetail get(long id, Long after) {
        return new StaffTicketDetail(row(id), messenger.after(id, after));
    }

    /** Nhận xử lý (idempotent với chính người đã nhận). 409 ticket_taken nếu người khác đã nhận, ticket_closed nếu đã đóng. */
    @Transactional
    public StaffTicketRow claim(long staffId, long id) {
        var before = row(id);
        claimOrFail(staffId, before);
        audit.record("TICKET_CLAIM", "SUPPORT_TICKET", id, Map.of("status", before.status()), Map.of("status", SupportTicket.IN_PROGRESS));
        return row(id);
    }

    /** Trả lời; ticket còn mở thì tự nhận. Học viên nhận thông báo đẩy (nội dung không nằm trong thông báo). */
    @Transactional
    public MessageView reply(long staffId, long id, String body) {
        claimOrFail(staffId, row(id));
        var m = messenger.add(id, staffId, true, body);
        notifications.ticketReply(row(id).studentId(), id, m.getId());
        return new MessageView(m.getId(), true, m.getBody(), m.getCreatedAt());
    }

    /** Idempotent. Nhân viên có quyền đóng ticket bất kỳ (ví dụ người nhận nghỉ giữa chừng). */
    @Transactional
    public StaffTicketRow close(long id) {
        var before = row(id);
        if (tickets.close(id, clock.instant()) > 0) {
            audit.record("TICKET_CLOSE", "SUPPORT_TICKET", id, Map.of("status", before.status()), Map.of("status", SupportTicket.CLOSED));
        }
        return row(id);
    }

    private void claimOrFail(long staffId, StaffTicketRow current) {
        if (tickets.claim(current.id(), staffId, clock.instant()) > 0) {
            return;
        }
        if (SupportTicket.CLOSED.equals(current.status())) {
            throw new ApiException(HttpStatus.CONFLICT, "ticket_closed", "Ticket đã đóng");
        }
        throw new ApiException(HttpStatus.CONFLICT, "ticket_taken", "Ticket đã có nhân viên khác nhận xử lý");
    }

    private StaffTicketRow row(long id) {
        return tickets.staffRow(id).stream().findFirst().map(TicketStaffService::row)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "ticket_not_found", "Không tìm thấy ticket"));
    }

    private static StaffTicketRow row(Object[] r) {
        return new StaffTicketRow(((Number) r[0]).longValue(), (String) r[1], (String) r[2], ((Number) r[3]).longValue(), (String) r[4],
                (String) r[5], r[6] == null ? null : ((Number) r[6]).longValue(), instant(r[7]), instant(r[8]));
    }

    private static Instant instant(Object o) {
        if (o instanceof Instant i) {
            return i;
        }
        if (o instanceof OffsetDateTime odt) {
            return odt.toInstant();
        }
        return ((Timestamp) o).toInstant();
    }
}
