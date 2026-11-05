package com.pps.smartenglish.profile.service;

import com.pps.smartenglish.auth.dto.AccountInfo;
import com.pps.smartenglish.auth.service.AccountService;
import com.pps.smartenglish.profile.domain.UserGoal;
import com.pps.smartenglish.profile.domain.UserProfile;
import com.pps.smartenglish.profile.dto.GoalInput;
import com.pps.smartenglish.profile.dto.GoalResponse;
import com.pps.smartenglish.profile.dto.MeResponse;
import com.pps.smartenglish.profile.dto.StatsResponse;
import com.pps.smartenglish.profile.dto.UpdateMeRequest;
import com.pps.smartenglish.profile.repository.UserGoalRepository;
import com.pps.smartenglish.profile.repository.UserProfileRepository;
import com.pps.smartenglish.shared.error.ApiException;
import com.pps.smartenglish.shared.event.UserCreated;
import java.util.List;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProfileService {

    private final UserProfileRepository profiles;
    private final UserGoalRepository goals;
    private final AccountService accounts;

    ProfileService(UserProfileRepository profiles, UserGoalRepository goals, AccountService accounts) {
        this.profiles = profiles;
        this.goals = goals;
        this.accounts = accounts;
    }

    /** Chạy trong transaction tạo tài khoản của auth. */
    @EventListener
    public void onUserCreated(UserCreated e) {
        profiles.save(new UserProfile(e.userId(), e.displayName()));
    }

    @Transactional(readOnly = true)
    public String displayName(long userId) {
        return profile(userId).getDisplayName();
    }

    @Transactional(readOnly = true)
    public MeResponse me(long userId) {
        AccountInfo a = accounts.get(userId);
        UserProfile p = profile(userId);
        return new MeResponse(a.id(), a.email(), a.emailVerified(), a.role(), p.getDisplayName(), p.getSelfLevel(),
                p.getTimezone());
    }

    @Transactional
    public MeResponse update(long userId, UpdateMeRequest req) {
        UserProfile p = profile(userId);
        if (req.displayName() != null) {
            p.setDisplayName(req.displayName().trim());
        }
        if (req.selfLevel() != null) {
            p.setSelfLevel(req.selfLevel());
        }
        return me(userId);
    }

    @Transactional(readOnly = true)
    public StatsResponse stats(long userId) {
        UserProfile p = profile(userId);
        return new StatsResponse(p.getXp(), p.getStreakCurrent(), p.getStreakBest(), p.getLastActiveDate());
    }

    @Transactional(readOnly = true)
    public List<GoalResponse> goals(long userId) {
        return goals.findByUserIdOrderById(userId).stream().map(ProfileService::toResponse).toList();
    }

    @Transactional
    public List<GoalResponse> replaceGoals(long userId, List<GoalInput> input) {
        goals.deleteAllByUser(userId);
        List<UserGoal> saved = goals.saveAll(input.stream()
                .map(g -> new UserGoal(userId, g.courseId(), g.targetType().trim(), g.targetValue().trim(), g.targetDate()))
                .toList());
        return saved.stream().map(ProfileService::toResponse).toList();
    }

    private UserProfile profile(long userId) {
        return profiles.findById(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "not_found", "Không tìm thấy hồ sơ"));
    }

    private static GoalResponse toResponse(UserGoal g) {
        return new GoalResponse(g.getId(), g.getCourseId(), g.getTargetType(), g.getTargetValue(), g.getTargetDate());
    }
}
