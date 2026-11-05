package com.pps.smartenglish.commerce.service;

import com.pps.smartenglish.auth.service.TokenUtil;
import com.pps.smartenglish.commerce.domain.ActivationRedemption;
import com.pps.smartenglish.commerce.dto.ActivationLinkDtos.Redeemed;
import com.pps.smartenglish.commerce.repository.ActivationLinkRepository;
import com.pps.smartenglish.commerce.repository.ActivationRedemptionRepository;
import com.pps.smartenglish.commerce.repository.ProductRepository;
import com.pps.smartenglish.shared.error.ApiException;
import com.pps.smartenglish.shared.event.ActivationLinkRedeemed;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Dùng link trong MỘT transaction: chèn redemption (unique chặn dùng lại và bấm đồng thời), giữ một lượt bằng UPDATE
 * có điều kiện, rồi cấp quyền. Redemption trùng ném DataIntegrityViolationException sau khi đã rollback;
 * ActivationLinkService đổi nó thành 409.
 */
@Component
public class RedemptionProcessor {

    private final ActivationLinkRepository links;
    private final ActivationRedemptionRepository redemptions;
    private final ProductRepository products;
    private final EntitlementGranter granter;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    RedemptionProcessor(ActivationLinkRepository links, ActivationRedemptionRepository redemptions, ProductRepository products,
                        EntitlementGranter granter, ApplicationEventPublisher events, Clock clock) {
        this.links = links;
        this.redemptions = redemptions;
        this.products = products;
        this.granter = granter;
        this.events = events;
        this.clock = clock;
    }

    @Transactional
    public Redeemed redeem(long userId, String token) {
        var link = links.findByTokenHash(TokenUtil.hash(token)).orElseThrow(RedemptionProcessor::invalid);
        Instant now = Instant.now(clock);
        var redemption = redemptions.saveAndFlush(new ActivationRedemption(link.getId(), userId, now));
        if (links.claimUse(link.getId(), now) == 0) {
            // rollback cả redemption; phân biệt hết hạn/thu hồi với hết lượt
            boolean usable = links.findById(link.getId()).map(l -> l.isUsable(now)).orElse(false);
            throw usable
                    ? new ApiException(HttpStatus.CONFLICT, "activation_link_exhausted", "Link kích hoạt đã hết lượt dùng")
                    : invalid();
        }
        var product = products.findWithLevels(List.of(link.getProductId())).get(0);
        var granted = granter.grant(userId, null, List.of(product), now);
        events.publishEvent(new ActivationLinkRedeemed(redemption.getId(), userId, granted.aiQuota()));
        return new Redeemed(product.getName(), granted.accessUntil());
    }

    static ApiException invalid() {
        return new ApiException(HttpStatus.NOT_FOUND, "activation_link_invalid", "Link kích hoạt không hợp lệ hoặc đã hết hạn");
    }
}
