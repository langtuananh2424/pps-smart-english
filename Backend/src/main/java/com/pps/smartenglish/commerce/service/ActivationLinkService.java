package com.pps.smartenglish.commerce.service;

import com.pps.smartenglish.auth.service.TokenUtil;
import com.pps.smartenglish.commerce.dto.ActivationLinkDtos.Preview;
import com.pps.smartenglish.commerce.dto.ActivationLinkDtos.Redeemed;
import com.pps.smartenglish.commerce.repository.ActivationLinkRepository;
import com.pps.smartenglish.commerce.repository.ProductRepository;
import com.pps.smartenglish.shared.error.ApiException;
import java.time.Clock;
import java.time.Instant;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** FR-COM-04, phía học viên: xem trước và dùng link. */
@Service
public class ActivationLinkService {

    private final ActivationLinkRepository links;
    private final ProductRepository products;
    private final RedemptionProcessor processor;
    private final EmailVerification emailVerification;
    private final Clock clock;

    ActivationLinkService(ActivationLinkRepository links, ProductRepository products, RedemptionProcessor processor,
                          EmailVerification emailVerification, Clock clock) {
        this.links = links;
        this.products = products;
        this.processor = processor;
        this.emailVerification = emailVerification;
        this.clock = clock;
    }

    /** Mã sai, hết hạn, đã thu hồi đều là 404 như nhau để không dò được mã. */
    @Transactional(readOnly = true)
    public Preview preview(String token) {
        var link = links.findByTokenHash(TokenUtil.hash(token)).filter(l -> l.isUsable(Instant.now(clock)))
                .orElseThrow(RedemptionProcessor::invalid);
        var p = products.findById(link.getProductId()).orElseThrow(RedemptionProcessor::invalid);
        return new Preview(p.getName(), p.getType(), p.getAccessDays(), p.getAiQuota());
    }

    /** Cố ý không @Transactional: transaction nằm trong RedemptionProcessor để bắt lỗi trùng sau khi nó đã rollback. */
    public Redeemed redeem(long userId, String token) {
        emailVerification.require(userId);
        try {
            return processor.redeem(userId, token);
        } catch (DataIntegrityViolationException e) {
            throw new ApiException(HttpStatus.CONFLICT, "activation_link_already_used", "Bạn đã dùng link kích hoạt này rồi");
        }
    }
}
