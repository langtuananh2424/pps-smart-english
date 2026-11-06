package com.pps.smartenglish.ai.service;

import com.pps.smartenglish.ai.domain.AiQuotaLedgerEntry;
import com.pps.smartenglish.ai.domain.AiQuotaLedgerEntry.Reason;
import com.pps.smartenglish.ai.domain.UserAiQuota;
import com.pps.smartenglish.ai.repository.AiQuotaLedgerRepository;
import com.pps.smartenglish.ai.repository.UserAiQuotaRepository;
import com.pps.smartenglish.shared.error.ApiException;
import com.pps.smartenglish.shared.event.ActivationLinkRedeemed;
import com.pps.smartenglish.shared.event.OrderPaid;
import com.pps.smartenglish.shared.event.UserCreated;
import java.util.Map;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AiQuotaService {

    private final UserAiQuotaRepository quotas;
    private final AiQuotaLedgerRepository ledger;
    private final AddonCatalog addons;

    AiQuotaService(UserAiQuotaRepository quotas, AiQuotaLedgerRepository ledger, AddonCatalog addons) {
        this.quotas = quotas;
        this.ledger = ledger;
        this.addons = addons;
    }

    @Transactional(readOnly = true)
    public int balance(long userId) {
        return quotas.findBalance(userId).orElse(0);
    }

    /** Bước không trừ lượt (câu hỏi đào sâu, bản nháp) vẫn cần số dư > 0 (Q-15). */
    @Transactional(readOnly = true)
    public void requirePositive(long userId) {
        if (balance(userId) < 1) {
            throw exhausted();
        }
    }

    /** Trừ 1 lượt rồi ghi sổ cái GRADING cho job; hết lượt thì 402 kèm gói nạp. Gọi trong transaction tạo job. */
    @Transactional
    public void debit(long userId, long jobId) {
        if (!tryDebit(userId, jobId)) {
            throw exhausted();
        }
    }

    /** Như debit nhưng trả false khi hết lượt, để nơi gọi tự quyết (nộp bài viết lại vẫn thành công). */
    @Transactional
    public boolean tryDebit(long userId, long jobId) {
        if (quotas.debitOne(userId) == 0) {
            return false;
        }
        ledger.save(new AiQuotaLedgerEntry(userId, -1, Reason.GRADING, jobId));
        return true;
    }

    /** Hoàn lượt của job lỗi hẳn. Nơi gọi chỉ gọi khi vừa chuyển job sang FAILED có điều kiện nên không hoàn hai lần. */
    @Transactional
    public void refund(long userId, long jobId) {
        credit(userId, 1, Reason.REFUND, jobId);
    }

    /** 402 kèm các gói AI_ADDON mở được (system-design 6.1). */
    public ApiException exhausted() {
        return new ApiException(HttpStatus.PAYMENT_REQUIRED, "quota_exhausted", "Bạn đã hết lượt chấm AI",
                Map.of("products", addons.addons()), null);
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
