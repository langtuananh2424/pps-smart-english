package com.pps.smartenglish.profile.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pps.smartenglish.profile.domain.UserProfile;
import com.pps.smartenglish.profile.dto.GoalInput;
import com.pps.smartenglish.profile.dto.QaDtos.QaAnswers;
import com.pps.smartenglish.profile.dto.QaDtos.QaRequest;
import com.pps.smartenglish.profile.dto.QaDtos.QaView;
import com.pps.smartenglish.profile.repository.UserProfileRepository;
import com.pps.smartenglish.shared.error.ApiException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** FR-GOAL-01, D-55: lưu bộ Q&A gợi ý khóa vào hồ sơ; làm lại thì ghi đè. */
@Service
public class QaService {

    /** Loại mục tiêu của dòng user_goals sinh từ Q&A. */
    static final String GOAL_TYPE = "IELTS";

    private final UserProfileRepository profiles;
    private final ProfileService profileService;
    private final ObjectMapper mapper;
    private final Clock clock;

    QaService(UserProfileRepository profiles, ProfileService profileService, ObjectMapper mapper, Clock clock) {
        this.profiles = profiles;
        this.profileService = profileService;
        this.mapper = mapper;
        this.clock = clock;
    }

    /** Ghi đè self_level, qa_answers và thay user_goals bằng một dòng mục tiêu band. */
    @Transactional
    public QaView save(long userId, QaRequest req) {
        UserProfile p = profiles.findById(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "not_found", "Không tìm thấy hồ sơ"));
        QaAnswers a = normalize(req);
        Instant now = Instant.now(clock);
        p.setSelfLevel(a.selfLevel());
        p.setQa(mapper.valueToTree(a), now);
        profileService.replaceGoals(userId, List.of(new GoalInput(null, GOAL_TYPE, a.targetBand().toPlainString(), null)));
        return QaView.of(a, now);
    }

    @Transactional(readOnly = true)
    public Optional<QaView> find(long userId) {
        return profiles.findById(userId).filter(p -> p.getQaAnswers() != null)
                .map(p -> QaView.of(read(p), p.getQaUpdatedAt()));
    }

    /** Câu trả lời đã lưu của người dùng; rỗng khi chưa làm Q&A. */
    @Transactional(readOnly = true)
    public Optional<QaAnswers> answers(long userId) {
        return profiles.findById(userId).filter(p -> p.getQaAnswers() != null).map(this::read);
    }

    /** targetBand về đúng một chữ số thập phân để lưu và so sánh nhất quán. */
    public static QaAnswers normalize(QaRequest r) {
        BigDecimal band = r.targetBand().setScale(1);
        return new QaAnswers(r.selfLevel(), List.copyOf(r.goals()), band, r.weeklyHours(), List.copyOf(r.skills()));
    }

    private QaAnswers read(UserProfile p) {
        try {
            return mapper.treeToValue(p.getQaAnswers(), QaAnswers.class);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalStateException("qa_answers hỏng ở user " + p.getUserId(), e);
        }
    }
}
