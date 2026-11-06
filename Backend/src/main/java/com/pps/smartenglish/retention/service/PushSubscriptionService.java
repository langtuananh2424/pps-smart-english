package com.pps.smartenglish.retention.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pps.smartenglish.retention.domain.PushSubscription;
import com.pps.smartenglish.retention.dto.PushDtos.SubscribeRequest;
import com.pps.smartenglish.retention.repository.PushSubscriptionRepository;
import com.pps.smartenglish.shared.error.ApiException;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/** FR-RET-03: đăng ký đầu nhận Web Push của trình duyệt. */
@Service
public class PushSubscriptionService {

    static final int MAX_PER_USER = 10;

    private final PushSubscriptionRepository subscriptions;
    private final PushEndpointPolicy policy;
    private final TransactionTemplate tx;
    private final ObjectMapper mapper;
    private final Clock clock;

    PushSubscriptionService(PushSubscriptionRepository subscriptions, PushEndpointPolicy policy, TransactionTemplate tx,
                            ObjectMapper mapper, Clock clock) {
        this.subscriptions = subscriptions;
        this.policy = policy;
        this.tx = tx;
        this.mapper = mapper;
        this.clock = clock;
    }

    /** Upsert theo endpoint: cùng trình duyệt đăng nhập người khác thì chuyển chủ. Quá 10 cái thì xóa cái cũ nhất. */
    public Long subscribe(long userId, SubscribeRequest req) {
        if (!policy.allows(req.endpoint())) {
            throw ApiException.validation("endpoint", "Endpoint push không hợp lệ hoặc không thuộc dịch vụ push được hỗ trợ");
        }
        try {
            return tx.execute(s -> upsert(userId, req));
        } catch (DataIntegrityViolationException e) {
            return tx.execute(s -> upsert(userId, req)); // hai request đồng thời cùng endpoint: lần này sẽ thấy dòng đã có
        }
    }

    /** Chỉ xóa của chính mình; endpoint của người khác hay không tồn tại đều im lặng như nhau. */
    public void unsubscribe(long userId, String endpoint) {
        subscriptions.deleteOwned(endpoint, userId);
    }

    private Long upsert(long userId, SubscribeRequest req) {
        var keys = mapper.createObjectNode().put("p256dh", req.keys().p256dh()).put("auth", req.keys().auth());
        var existing = subscriptions.findByEndpoint(req.endpoint());
        PushSubscription sub;
        if (existing.isPresent()) {
            sub = existing.get();
            sub.reassign(userId, keys);
        } else {
            sub = new PushSubscription(userId, req.endpoint(), keys, Instant.now(clock));
        }
        sub = subscriptions.saveAndFlush(sub);
        List<PushSubscription> mine = subscriptions.findByUserIdOrderByIdAsc(userId);
        for (int i = 0; i < mine.size() - MAX_PER_USER; i++) {
            subscriptions.deleteById(mine.get(i).getId());
        }
        return sub.getId();
    }
}
