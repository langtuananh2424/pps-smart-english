package com.pps.smartenglish.exercise.service;

import com.pps.smartenglish.exercise.content.RichText;
import com.pps.smartenglish.exercise.domain.Question;
import com.pps.smartenglish.exercise.domain.QuestionChoice;
import com.pps.smartenglish.exercise.domain.QuestionHint;
import com.pps.smartenglish.exercise.domain.QuestionBank;
import com.pps.smartenglish.exercise.domain.StudentAnswer;
import com.pps.smartenglish.exercise.dto.BankResponse;
import com.pps.smartenglish.exercise.dto.BankResponse.HintView;
import com.pps.smartenglish.exercise.repository.QuestionHintRepository;
import com.pps.smartenglish.exercise.repository.StudentAnswerRepository;
import com.pps.smartenglish.exercise.repository.StudentAttemptRepository;
import com.pps.smartenglish.shared.error.ApiException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** D-152: đề của lượt Luyện thêm dưới dạng "bộ" để màn làm bài và kết quả dùng lại; chỉ chủ lượt xem được, không có đáp án. */
@Service
public class ExtraAttemptViewService {

    private final StudentAttemptRepository attempts;
    private final StudentAnswerRepository answers;
    private final QuestionLoader loader;
    private final QuestionHintRepository hints;
    private final QuestionMediaService media;
    private final RichText rich;

    ExtraAttemptViewService(StudentAttemptRepository attempts, StudentAnswerRepository answers, QuestionLoader loader,
                            QuestionHintRepository hints, QuestionMediaService media, RichText rich) {
        this.attempts = attempts;
        this.answers = answers;
        this.loader = loader;
        this.hints = hints;
        this.media = media;
        this.rich = rich;
    }

    @Transactional(readOnly = true)
    public BankResponse get(long userId, long attemptId) {
        var a = attempts.findByIdAndUserId(attemptId, userId).filter(x -> x.isExtra())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "attempt_not_found", "Không tìm thấy lượt làm bài"));
        var set = a.getSubmittedAt() == null ? loader.load(a)
                : loader.loadForHistory(a, answers.findByAttemptId(a.getId()).stream().map(StudentAnswer::getQuestionId).toList());
        var ids = set.questions().stream().map(Question::getId).toList();
        Map<Long, List<HintView>> hintMap = ids.isEmpty() ? Map.of()
                : hints.findByQuestionIdInOrderBySortOrderAscIdAsc(ids).stream().collect(Collectors.groupingBy(QuestionHint::getQuestionId,
                        Collectors.mapping(h -> new HintView(h.getId(), h.getContent(), h.getDetail()), Collectors.toList())));
        var html = rich.render(Stream.concat(set.questions().stream().map(Question::getPromptHtml),
                set.choices().values().stream().flatMap(List::stream).map(QuestionChoice::getContentHtml)).toList());
        var views = BankService.questionViews(set, hintMap, media.forQuestions(ids), html, a.getId());
        return new BankResponse(a.getBankId(), "Luyện thêm", null, false, QuestionBank.PRACTICE, null, views, List.of(),
                QuestionBank.FREE, null, null);
    }
}
