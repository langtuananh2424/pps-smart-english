package com.pps.smartenglish.retention.service;

import com.pps.smartenglish.profile.repository.UserProfileRepository;
import com.pps.smartenglish.retention.repository.XpEventRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** FR-RET-02: cộng XP qua xp_events (unique chống cộng trùng). */
@Service
public class XpService {

    private final XpEventRepository events;
    private final UserProfileRepository profiles;

    XpService(XpEventRepository events, UserProfileRepository profiles) {
        this.events = events;
        this.profiles = profiles;
    }

    /** Chèn được dòng mới thì mới cộng, bằng một câu UPDATE nguyên tử. false = đã thưởng việc này rồi. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean grant(long userId, String source, long refId, int amount) {
        if (amount <= 0 || events.insertIfAbsent(userId, source, refId, amount) == 0) {
            return false;
        }
        profiles.addXp(userId, amount);
        return true;
    }
}
