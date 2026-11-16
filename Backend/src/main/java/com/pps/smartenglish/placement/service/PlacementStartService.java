package com.pps.smartenglish.placement.service;

import com.pps.smartenglish.exercise.domain.Question;
import com.pps.smartenglish.exercise.domain.QuestionBank;
import com.pps.smartenglish.exercise.dto.BankResponse.QuestionView;
import com.pps.smartenglish.exercise.repository.QuestionBankRepository;
import com.pps.smartenglish.exercise.service.BankService;
import com.pps.smartenglish.exercise.service.QuestionLoader;
import com.pps.smartenglish.placement.domain.PlacementAttempt;
import com.pps.smartenglish.placement.domain.PlacementConfig;
import com.pps.smartenglish.placement.dto.PlacementDtos.QuestionOut;
import com.pps.smartenglish.placement.dto.PlacementDtos.Started;
import com.pps.smartenglish.placement.repository.PlacementAttemptRepository;
import com.pps.smartenglish.placement.repository.PlacementConfigRepository;
import com.pps.smartenglish.shared.error.ApiException;
import com.pps.smartenglish.shared.ratelimit.RateLimiter;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * FR-TEST-01: bắt đầu lượt. Khách (không đăng nhập) nhận token một lần và bị giới hạn theo IP và toàn cục; học viên bị
 * giới hạn bởi số lần làm lại (D-23). Đề trả về không kèm đáp án.
 */
@Service
public class PlacementStartService {

    private static final Duration WINDOW = Duration.ofHours(1);

    private final PlacementAttemptRepository attempts;
    private final QuestionBankRepository banks;
    private final PlacementConfigRepository config;
    private final QuestionLoader loader;
    private final PlacementSubmitService submits;
    private final RateLimiter limiter;
    private final Clock clock;
    private final int ipLimit;
    private final int globalLimit;

    PlacementStartService(PlacementAttemptRepository attempts, QuestionBankRepository banks, PlacementConfigRepository config,
                          QuestionLoader loader, PlacementSubmitService submits, RateLimiter limiter, Clock clock,
                          @Value("${app.placement.guest-ip-limit:5}") int ipLimit,
                          @Value("${app.placement.guest-global-limit:500}") int globalLimit) {
        this.attempts = attempts;
        this.banks = banks;
        this.config = config;
        this.loader = loader;
        this.submits = submits;
        this.limiter = limiter;
        this.clock = clock;
        this.ipLimit = ipLimit;
        this.globalLimit = globalLimit;
    }

    @Transactional
    public Started start(Long userId, String ip) {
        QuestionBank bank = banks.findByPurposeAndStatusOrderByIdDesc(QuestionBank.PLACEMENT, QuestionBank.PUBLISHED).stream()
                .findFirst().orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "placement_unavailable",
                        "Chưa có đề kiểm tra đầu vào"));
        Instant now = Instant.now(clock);
        if (userId == null) {
            limitGuest(ip);
            String token = PlacementTokens.generate();
            var a = attempts.save(new PlacementAttempt(bank.getId(), null, PlacementTokens.hash(token), now, bank.getTimeLimitSec()));
            return view(a, bank, token, now);
        }
        attempts.lockUser(userId);
        var open = attempts.findFirstByUserIdAndSubmittedAtIsNullOrderByIdDesc(userId);
        if (open.isPresent()) {
            var a = open.get();
            if (!PlacementSubmitService.pastGrace(a, now)) {
                return view(a, banks.findById(a.getBankId()).orElseThrow(), null, now);
            }
            submits.autoSubmit(a.getId()); // lượt quá hạn tính là đã dùng một lần
        }
        long used = attempts.countByUserIdAndSubmittedAtIsNotNull(userId);
        int allowed = config.findById(PlacementConfig.ID).orElseThrow().allowedAttempts();
        if (used >= allowed) {
            throw new ApiException(HttpStatus.CONFLICT, "placement_retakes_exhausted", "Đã hết số lần làm kiểm tra đầu vào",
                    Map.of("used", used, "allowed", allowed), null);
        }
        var a = attempts.save(new PlacementAttempt(bank.getId(), userId, null, now, bank.getTimeLimitSec()));
        return view(a, bank, null, now);
    }

    /** Hai cửa sổ cố định theo giờ: theo IP, rồi toàn hệ thống (chặn xoay IP làm đầy bảng lượt). */
    private void limitGuest(String ip) {
        limiter.requireHit("placement:ip:" + ip, ipLimit, WINDOW, "placement:ip:" + ip);
        limiter.requireHit("placement:global", globalLimit, WINDOW, "placement:global");
    }

    private Started view(PlacementAttempt a, QuestionBank bank, String guestToken, Instant now) {
        var set = loader.load(a.getBankId());
        List<QuestionView> views = BankService.questionViews(set, Map.of());
        List<Question> qs = set.questions();
        List<QuestionOut> out = IntStream.range(0, qs.size()).mapToObj(i -> {
            var v = views.get(i);
            return new QuestionOut(v.id(), qs.get(i).getSkill(), v.type(), v.prompt(), v.payload(), v.choices());
        }).toList();
        return new Started(a.getId(), guestToken, bank.getTitle(), a.getStartedAt(), a.getDeadlineAt(), a.getTimeLimitSec(),
                PlacementSubmitService.remainingSec(a, now), a.getDraftAnswers(), out);
    }
}
