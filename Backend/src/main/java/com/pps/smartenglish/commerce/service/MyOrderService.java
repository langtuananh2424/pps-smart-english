package com.pps.smartenglish.commerce.service;

import com.pps.smartenglish.commerce.domain.ShopOrder;
import com.pps.smartenglish.commerce.dto.OrderSummary;
import com.pps.smartenglish.commerce.repository.OrderItemRepository;
import com.pps.smartenglish.commerce.repository.OrderRepository;
import com.pps.smartenglish.shared.web.CursorPage;
import com.pps.smartenglish.shared.web.Cursors;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** D-75: lịch sử đơn của chính học viên, chỉ đọc. */
@Service
public class MyOrderService {

    private static final int DEFAULT_LIMIT = 20;
    private static final int MAX_LIMIT = 50;

    private final OrderRepository orders;
    private final OrderItemRepository items;
    private final Clock clock;

    MyOrderService(OrderRepository orders, OrderItemRepository items, Clock clock) {
        this.orders = orders;
        this.items = items;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public CursorPage<OrderSummary> list(long userId, String limit, String cursor) {
        int size = Cursors.size(limit, DEFAULT_LIMIT, MAX_LIMIT);
        var rows = orders.pageByUser(userId, Cursors.decode(cursor), PageRequest.of(0, size + 1));
        boolean more = rows.size() > size;
        var page = more ? rows.subList(0, size) : rows;
        Map<Long, List<OrderSummary.Item>> byOrder = new HashMap<>();
        if (!page.isEmpty()) {
            for (Object[] r : items.findNames(page.stream().map(ShopOrder::getId).toList())) {
                byOrder.computeIfAbsent((Long) r[0], k -> new ArrayList<>()).add(new OrderSummary.Item((Long) r[1], (String) r[2]));
            }
        }
        Instant now = Instant.now(clock);
        var data = page.stream().map(o -> new OrderSummary(o.getCode(), o.effectiveStatus(now), o.getTotalVnd(), o.getCreatedAt(),
                o.getPaidAt(), o.getExpiresAt(), byOrder.getOrDefault(o.getId(), List.of()))).toList();
        return new CursorPage<>(data, more ? Cursors.encode(page.get(size - 1).getId()) : null);
    }
}
