package com.pps.smartenglish.ai.service;

import com.pps.smartenglish.ai.domain.UserAiQuota;
import com.pps.smartenglish.ai.repository.UserAiQuotaRepository;
import com.pps.smartenglish.shared.event.UserCreated;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

@Service
public class AiQuotaService {

    private final UserAiQuotaRepository quotas;

    AiQuotaService(UserAiQuotaRepository quotas) {
        this.quotas = quotas;
    }

    /** Tài khoản mới có 0 lượt; lượt chỉ được cấp khi mua khóa. Chạy trong transaction tạo tài khoản. */
    @EventListener
    public void onUserCreated(UserCreated e) {
        quotas.save(new UserAiQuota(e.userId(), 0));
    }
}
