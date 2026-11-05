package com.pps.smartenglish.ai.service;

import com.pps.smartenglish.ai.domain.AiQuotaLedgerEntry;
import com.pps.smartenglish.ai.domain.AiQuotaLedgerEntry.Reason;
import com.pps.smartenglish.ai.domain.UserAiQuota;
import com.pps.smartenglish.ai.repository.AiQuotaLedgerRepository;
import com.pps.smartenglish.ai.repository.UserAiQuotaRepository;
import com.pps.smartenglish.shared.event.ActivationLinkRedeemed;
import com.pps.smartenglish.shared.event.OrderPaid;
import com.pps.smartenglish.shared.event.UserCreated;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

@Service
public class AiQuotaService {

    private final UserAiQuotaRepository quotas;
    private final AiQuotaLedgerRepository ledger;

    AiQuotaService(UserAiQuotaRepository quotas, AiQuotaLedgerRepository ledger) {
        this.quotas = quotas;
        this.ledger = ledger;
    }

    /** Tài khoản mới có 0 lượt; lượt chỉ được cấp khi mua khóa. Chạy trong transaction tạo tài khoản. */
    @EventListener
    public void onUserCreated(UserCreated e) {
        quotas.save(new UserAiQuota(e.userId(), 0));
    }

    /** Chạy trong transaction kích hoạt đơn: cộng số dư và ghi sổ cái cùng lúc, hoặc cùng không. */
    @EventListener
    public void onOrderPaid(OrderPaid e) {
        credit(e.userId(), e.aiQuota(), Reason.PURCHASE, e.orderId());
    }

    /** Chạy trong transaction dùng link kích hoạt; ref là id dòng activation_redemptions. */
    @EventListener
    public void onActivationLinkRedeemed(ActivationLinkRedeemed e) {
        credit(e.userId(), e.aiQuota(), Reason.ACTIVATION_LINK, e.redemptionId());
    }

    private void credit(long userId, int amount, Reason reason, long refId) {
        if (amount <= 0) {
            return;
        }
        if (quotas.addBalance(userId, amount) == 0) {
            quotas.save(new UserAiQuota(userId, amount));
        }
        ledger.save(new AiQuotaLedgerEntry(userId, amount, reason, refId));
    }
}
