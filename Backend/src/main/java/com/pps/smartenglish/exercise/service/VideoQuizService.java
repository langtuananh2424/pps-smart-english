package com.pps.smartenglish.exercise.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.pps.smartenglish.exercise.config.VideoQuizProperties;
import com.pps.smartenglish.exercise.domain.Question;
import com.pps.smartenglish.exercise.domain.StudentAttempt;
import com.pps.smartenglish.exercise.domain.VideoProgress;
import com.pps.smartenglish.exercise.dto.SubmitRequest;
import com.pps.smartenglish.exercise.dto.SubmitRequest.AnswerInput;
import com.pps.smartenglish.exercise.dto.VideoQuizDtos.GroupState;
import com.pps.smartenglish.exercise.dto.VideoQuizDtos.ProgressRequest;
import com.pps.smartenglish.exercise.dto.VideoQuizDtos.ProgressResult;
import com.pps.smartenglish.exercise.dto.VideoQuizDtos.QuestionOutcome;
import com.pps.smartenglish.exercise.dto.VideoQuizDtos.QuizAnswer;
import com.pps.smartenglish.exercise.dto.VideoQuizDtos.State;
import com.pps.smartenglish.exercise.grading.Credit;
import com.pps.smartenglish.exercise.grading.GraderRegistry;
import com.pps.smartenglish.exercise.repository.QuestionGroupRepository;
import com.pps.smartenglish.exercise.repository.StudentAttemptRepository;
import com.pps.smartenglish.exercise.repository.VideoProgressRepository;
import com.pps.smartenglish.media.service.VideoMediaService;
import com.pps.smartenglish.shared.error.ApiException;
import com.pps.smartenglish.shared.ratelimit.RateLimiter;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * FR-EX-04, D-159: video từ nối. Một lượt làm bài là một lượt xem: tiến độ xem lưu theo (lượt, nhóm), quiz của nhóm chỉ mở khi xem
 * đủ, tối đa maxSubmits lần nộp. Điểm vẫn chấm lúc nộp lượt, nhưng câu video chỉ lấy câu trả lời của lần nộp quiz đã chốt.
 * Chỉ chủ lượt, lượt đang mở mới báo tiến độ và nộp quiz (người khác nhận 404).
 */
@Service
public class VideoQuizService {

    private final StudentAttemptRepository attempts;
    private final VideoProgressRepository rows;
    private final QuestionLoader loader;
    private final GraderRegistry graders;
    private final AssignedBanks assigned;
    private final RateLimiter limiter;
    private final VideoQuizProperties props;
    private final ObjectMapper mapper;
    private final Clock clock;
    private final QuestionGroupRepository groups;
    private final VideoMediaService videos;

    VideoQuizService(StudentAttemptRepository attempts, VideoProgressRepository rows, QuestionLoader loader, GraderRegistry graders,
                     AssignedBanks assigned, RateLimiter limiter, VideoQuizProperties props, ObjectMapper mapper, Clock clock,
                     QuestionGroupRepository groups, VideoMediaService videos) {
        this.groups = groups;
        this.videos = videos;
        this.attempts = attempts;
        this.rows = rows;
        this.loader = loader;
        this.graders = graders;
        this.assigned = assigned;
        this.limiter = limiter;
        this.props = props;
        this.mapper = mapper;
        this.clock = clock;
    }

    @Transactional
    public ProgressResult report(long userId, long attemptId, ProgressRequest req) {
        limiter.requireHit("rl:video-progress:" + userId, props.reportsPerMinute(), Duration.ofMinutes(1), "video-progress:" + userId);
        var a = openAttempt(userId, attemptId);
        groupQuestions(a, req.groupId()); // 404 nếu nhóm không phải nhóm video của bộ này
        double duration = durationOf(req.groupId());
        var p = rows.findByAttemptIdAndGroupId(attemptId, req.groupId()).orElseGet(() -> new VideoProgress(attemptId, req.groupId()));
        p.watch(req.position(), duration, Instant.now(clock), props.toleranceSec());
        rows.save(p);
        return new ProgressResult(p.getGroupId(), p.getFurthestSec(), p.getDurationSec(), p.watched(props.watchPct()));
    }

    /** Nộp quiz của một nhóm: trả đúng/sai từng câu; đáp án đúng và giải thích chỉ khi đã chốt. */
    @Transactional
    public GroupState submitQuiz(long userId, long attemptId, long groupId, List<QuizAnswer> answers) {
        var a = openAttempt(userId, attemptId);
        var set = loader.load(a);
        var questions = groupQuestions(a, groupId, set);
        var p = rows.findByAttemptIdAndGroupId(attemptId, groupId).orElse(null);
        if (p == null || !p.watched(props.watchPct())) {
            throw new ApiException(HttpStatus.CONFLICT, "video_not_watched", "Hãy xem hết video trước khi làm quiz");
        }
        if (p.isFinalized()) {
            throw new ApiException(HttpStatus.CONFLICT, "video_quiz_finalized", "Quiz của video này đã chốt");
        }
        Map<Long, JsonNode> given = indexAnswers(answers, questions);
        List<Long> wrong = new ArrayList<>();
        for (Question q : questions) {
            var credit = graders.credit(q.getType(), set.inputOf(q), given.get(q.getId()));
            if (!Credit.isFull(credit)) {
                wrong.add(q.getId());
            }
        }
        ArrayNode stored = mapper.createArrayNode();
        questions.forEach(q -> stored.add(mapper.createObjectNode().put("questionId", q.getId()).set("answer", given.get(q.getId()))));
        p.recordSubmit(stored, mapper.valueToTree(wrong), wrong.isEmpty(), props.maxSubmits());
        rows.save(p);
        return groupState(p, questions, set);
    }

    @Transactional(readOnly = true)
    public State state(long userId, long attemptId) {
        var a = attempts.findByIdAndUserId(attemptId, userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "attempt_not_found", "Không tìm thấy lượt làm bài"));
        var set = loader.load(a);
        Map<Long, List<Question>> byGroup = videoQuestions(set.questions());
        Map<Long, VideoProgress> saved = rows.findByAttemptId(attemptId).stream().collect(Collectors.toMap(VideoProgress::getGroupId, p -> p));
        List<GroupState> groups = new ArrayList<>();
        byGroup.forEach((gid, qs) -> groups.add(groupState(saved.getOrDefault(gid, new VideoProgress(attemptId, gid)), qs, set)));
        return new State((int) rows.attemptNo(userId, a.getBankId(), attemptId), rows.passedAttempts(userId, a.getBankId()),
                props.passesRequired(), groups);
    }

    /**
     * Nộp lượt: câu video chỉ lấy câu trả lời của quiz đã chốt, còn lại bỏ trống (không tin giá trị client gửi, nên không né được
     * việc xem video hay giới hạn số lần nộp).
     */
    public SubmitRequest pinAnswers(long attemptId, SubmitRequest req, List<Question> questions) {
        Map<Long, List<Question>> byGroup = videoQuestions(questions);
        if (byGroup.isEmpty()) {
            return req;
        }
        Set<Long> videoIds = byGroup.values().stream().flatMap(List::stream).map(Question::getId).collect(Collectors.toSet());
        Map<Long, Long> times = new HashMap<>();
        List<AnswerInput> out = new ArrayList<>();
        for (AnswerInput in : req.answers()) {
            if (videoIds.contains(in.questionId())) {
                times.put(in.questionId(), in.timeSpentMs());
            } else {
                out.add(in);
            }
        }
        for (VideoProgress p : rows.findByAttemptId(attemptId)) {
            if (p.isFinalized() && p.getAnswers() != null) {
                p.getAnswers().forEach(n -> {
                    long qid = n.path("questionId").asLong();
                    if (videoIds.contains(qid) && !n.path("answer").isNull()) {
                        out.add(new AnswerInput(qid, n.get("answer"), times.get(qid)));
                    }
                });
            }
        }
        return new SubmitRequest(out);
    }

    /** Lượt đạt khi mọi nhóm video của bộ đã chốt và đúng hết; null khi bộ không có câu video (luật Đạt/Chưa đạt thường áp dụng). */
    public Boolean attemptPassed(long attemptId, List<Question> questions) {
        Map<Long, List<Question>> byGroup = videoQuestions(questions);
        if (byGroup.isEmpty()) {
            return null;
        }
        Set<Long> passed = rows.findByAttemptId(attemptId).stream().filter(VideoProgress::isPassed).map(VideoProgress::getGroupId)
                .collect(Collectors.toSet());
        return passed.containsAll(byGroup.keySet());
    }

    private GroupState groupState(VideoProgress p, List<Question> questions, QuestionSet set) {
        List<QuizAnswer> answers = null;
        if (p.getAnswers() != null) {
            answers = new ArrayList<>();
            for (JsonNode n : p.getAnswers()) {
                answers.add(new QuizAnswer(n.path("questionId").asLong(), n.get("answer")));
            }
        }
        Set<Long> wrong = new java.util.HashSet<>();
        if (p.getWrongIds() != null) {
            p.getWrongIds().forEach(n -> wrong.add(n.asLong()));
        }
        List<QuestionOutcome> results = null;
        if (p.getSubmits() > 0) {
            results = questions.stream().map(q -> p.isFinalized()
                    ? new QuestionOutcome(q.getId(), !wrong.contains(q.getId()), graders.correctAnswer(q.getType(), set.inputOf(q)), q.getExplanation())
                    : new QuestionOutcome(q.getId(), !wrong.contains(q.getId()), null, null)).toList();
        }
        return new GroupState(p.getGroupId(), p.getFurthestSec(), p.getDurationSec(), p.watched(props.watchPct()), p.getSubmits(),
                props.maxSubmits(), p.isFinalized(), p.isPassed(), answers, results);
    }

    /** Lượt của chính học viên, đang mở, còn hạn; khóa dòng lượt để hai lần nộp quiz song song không vượt số lần. */
    private StudentAttempt openAttempt(long userId, long attemptId) {
        var a = attempts.lockById(attemptId).filter(x -> x.getUserId() == userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "attempt_not_found", "Không tìm thấy lượt làm bài"));
        if (a.getSubmittedAt() != null) {
            throw new ApiException(HttpStatus.CONFLICT, "attempt_already_submitted", "Lượt làm bài này đã được nộp");
        }
        assigned.requireActive(a.getAssignmentId()); // D-153
        if (a.expired(Instant.now(clock))) {
            throw new ApiException(HttpStatus.CONFLICT, "attempt_time_up", "Đã hết giờ làm bài");
        }
        return a;
    }

    /** Thời lượng do máy chủ giữ: mvhd của file lúc tải lên, hoặc số CMS nhập cho YouTube. Thiếu thì 409, không tin client. */
    private double durationOf(long groupId) {
        var g = groups.findById(groupId).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "group_not_found", "Không tìm thấy nhóm video trong lượt này"));
        Integer sec = g.getVideoAssetId() != null ? videos.durationSec(g.getVideoAssetId()).orElse(null) : g.getVideoDurationSec();
        if (sec == null || sec < 1) {
            throw new ApiException(HttpStatus.CONFLICT, "video_duration_missing", "Video chưa có thời lượng, hãy báo người soạn đề");
        }
        return sec;
    }

    private List<Question> groupQuestions(StudentAttempt a, long groupId) {
        return groupQuestions(a, groupId, loader.load(a));
    }

    private List<Question> groupQuestions(StudentAttempt a, long groupId, QuestionSet set) {
        var qs = videoQuestions(set.questions()).get(groupId);
        if (qs == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "group_not_found", "Không tìm thấy nhóm video trong lượt này");
        }
        return qs;
    }

    /** Câu video còn hiệu lực theo nhóm, giữ thứ tự bộ. */
    private static Map<Long, List<Question>> videoQuestions(List<Question> questions) {
        Map<Long, List<Question>> out = new LinkedHashMap<>();
        for (Question q : questions) {
            if (q.getType().needsVideo() && q.getGroupId() != null) {
                out.computeIfAbsent(q.getGroupId(), k -> new ArrayList<>()).add(q);
            }
        }
        return out;
    }

    /** Mỗi câu của nhóm có đúng một câu trả lời, có ít nhất một lựa chọn: thiếu thì 400 và không tốn lần nộp. */
    private static Map<Long, JsonNode> indexAnswers(List<QuizAnswer> answers, List<Question> questions) {
        Set<Long> valid = questions.stream().map(Question::getId).collect(Collectors.toSet());
        Map<Long, JsonNode> out = new HashMap<>();
        for (int i = 0; i < answers.size(); i++) {
            var in = answers.get(i);
            String field = "answers[" + i + "]";
            if (!valid.contains(in.questionId())) {
                throw ApiException.validation(field + ".questionId", "Câu hỏi không thuộc nhóm video này");
            }
            if (in.answer() == null || !in.answer().path("choiceIds").isArray() || in.answer().get("choiceIds").isEmpty()) {
                throw ApiException.validation(field + ".answer", "Hãy chọn ít nhất một đáp án");
            }
            if (out.put(in.questionId(), in.answer()) != null) {
                throw ApiException.validation(field + ".questionId", "Câu hỏi bị lặp");
            }
        }
        if (out.size() != valid.size()) {
            throw ApiException.validation("answers", "Cần trả lời hết các câu của video");
        }
        return out;
    }
}
