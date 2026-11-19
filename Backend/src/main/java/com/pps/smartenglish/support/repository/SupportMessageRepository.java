package com.pps.smartenglish.support.repository;

import com.pps.smartenglish.support.domain.SupportMessage;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SupportMessageRepository extends JpaRepository<SupportMessage, Long> {

    /** Tin nhắn mới hơn after (tăng dần): giao diện hỏi lại định kỳ với id tin cuối đã có. */
    List<SupportMessage> findByTicketIdAndIdGreaterThanOrderByIdAsc(Long ticketId, Long after, Pageable page);
}
