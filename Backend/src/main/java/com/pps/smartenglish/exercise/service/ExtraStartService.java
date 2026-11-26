package com.pps.smartenglish.exercise.service;

import com.pps.smartenglish.catalog.repository.CourseRepository;
import com.pps.smartenglish.exercise.config.ExtraProperties;
import com.pps.smartenglish.exercise.domain.QuestionBank;
import com.pps.smartenglish.exercise.domain.QuestionType;
import com.pps.smartenglish.exercise.domain.StudentAttempt;
import com.pps.smartenglish.exercise.dto.AttemptStarted;
import com.pps.smartenglish.exercise.dto.ExtraDtos.StartRequest;
import com.pps.smartenglish.exercise.repository.AttemptQuestionRepository;
import com.pps.smartenglish.exercise.repository.ExtraPoolRepository;
import com.pps.smartenglish.exercise.repository.ExtraPoolRepository.Candidate;
import com.pps.smartenglish.exercise.repository.QuestionBankRepository;
import com.pps.smartenglish.exercise.repository.StudentAttemptRepository;
import com.pps.smartenglish.shared.error.ApiException;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * FR-EX-07, D-152: bắt đầu lượt Luyện thêm. Câu chọn ở máy chủ từ Topic học viên được phép (quyền kiểm lại ở đây, không tin client);
 * lượt gắn bộ neo nên không tính vào tiến độ Topic/Unit, còn chấm, nháp, nộp, thử lại, Viết/Nói dùng lại luồng lượt thường.
 */
@Service
public class ExtraStartService {

    private static final String REVIEW = "REVIEW";
    private static final String CHALLENGE = "CHALLENGE";

    private final CourseRepository courses;
    private final ExtraTopics topics;
    private final ExtraPoolRepository pool;
    private final ExtraProperties props;
    private final AttemptService attemptService;
    private final StudentAttemptRepository attempts;
    private final AttemptQuestionRepository attemptQuestions;
    private final QuestionBankRepository banks;
    private final Clock clock;
    private final Random random = new Random();

    ExtraStartService(CourseRepository courses, ExtraTopics topics, ExtraPoolRepository pool, ExtraProperties props,
                      AttemptService attemptService, StudentAttemptRepository attempts, AttemptQuestionRepository attemptQuestions,
                      QuestionBankRepository banks, Clock clock) {
        this.courses = courses;
        this.topics = topics;
        this.pool = pool;
        this.props = props;
        this.attemptService = attemptService;
        this.attempts = attempts;
        this.attemptQuestions = attemptQuestions;
        this.banks = banks;
        this.clock = clock;
    }

    @Transactional
    public AttemptStarted start(long userId, long courseId, StartRequest req) {
        if (req == null) {
            throw ApiException.validation("scope", "Thiếu nội dung yêu cầu");
        }
        String scope = req.scope();
        if (!REVIEW.equals(scope) && !CHALLENGE.equals(scope)) {
            throw ApiException.validation("scope", "Chỉ nhận REVIEW hoặc CHALLENGE");
        }
        if (CHALLENGE.equals(scope) && req.topicId() == null) {
            throw ApiException.validation("topicId", "Thử thách cần chọn Topic");
        }
        var kind = ExtraKind.parse(req.kind());
        var course = ExtraOverviewService.requireCourse(courses, courseId);
        var anchor = banks.findByPurposeOrderByIdAsc(QuestionBank.EXTRA).stream().findFirst()
                .orElseThrow(() -> new IllegalStateException("Thiếu bộ neo Luyện thêm (V44)"));
        attemptService.requireNoOpenAttempt(userId, anchor.getId()); // khóa dòng user, một lượt Luyện thêm mở mỗi lần

        var wanted = CHALLENGE.equals(scope) ? ExtraTopics.OPEN : ExtraTopics.COMPLETED;
        var eligible = topics.of(userId, course).stream()
                .filter(s -> s.state().equals(wanted) && (req.topicId() == null || s.row().topicId() == req.topicId())).toList();
        if (req.topicId() != null && eligible.isEmpty()) { // chưa mua, khóa tuần tự, chưa hoàn thành hoặc không thuộc khóa
            throw new ApiException(HttpStatus.FORBIDDEN, "extra_topic_not_available", "Bạn chưa thể luyện thêm Topic này");
        }
        var access = ExtraTopics.accessByTopic(eligible);
        var topicIds = eligible.stream().map(s -> s.row().topicId()).toList();

        // Trần theo tổng số câu hợp lệ: Thử thách theo cả Topic, Ôn lại theo dạng đã lọc (hai bên chỉ tính dạng học viên được làm)
        var totalKind = CHALLENGE.equals(scope) ? null : kind;
        int total = pool.counts(userId, courseId, ExtraKind.allTypes()).stream()
                .filter(c -> access.containsKey(c.topicId())
                        && (totalKind == null || ExtraKind.of(QuestionType.valueOf(c.type())) == totalKind)
                        && ExtraTopics.allows(access.get(c.topicId()), ExtraKind.of(QuestionType.valueOf(c.type()))))
                .mapToInt(c -> c.n()).sum();
        var candidates = pool.candidates(userId, courseId, topicIds, kind == null ? ExtraKind.allTypes() : kind.types(), props.poolLimit())
                .stream().filter(c -> ExtraTopics.allows(access.get(c.topicId()), ExtraKind.of(QuestionType.valueOf(c.type())))).toList();
        if (candidates.isEmpty()) {
            throw new ApiException(HttpStatus.CONFLICT, "extra_no_questions", "Chưa có câu phù hợp để luyện thêm");
        }
        int size = props.capped(CHALLENGE.equals(scope) ? props.challengeSize() : props.reviewSize(), total);
        List<Long> ids = candidates.stream().map(Candidate::questionId).toList();
        var picked = ExtraPicker.pick(ids, pool.wrongCounts(userId, ids), size, random);

        var a = attempts.save(new StudentAttempt(userId, anchor.getId(), Instant.now(clock), null).withMode(QuestionBank.PRACTICE).asExtra());
        attemptQuestions.add(a.getId(), picked);
        return new AttemptStarted(a.getId(), a.getBankId(), a.getStartedAt(), a.getTimeLimitSec(), a.getMode());
    }
}
