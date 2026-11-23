package com.pps.smartenglish.exercise.service;

import com.pps.smartenglish.exercise.content.RichText;
import com.pps.smartenglish.exercise.domain.GrammarElement;
import com.pps.smartenglish.exercise.domain.Question;
import com.pps.smartenglish.exercise.domain.QuestionChoice;
import com.pps.smartenglish.exercise.domain.QuestionTag;
import com.pps.smartenglish.exercise.dto.CmsQuestionDtos.ChoiceView;
import com.pps.smartenglish.exercise.dto.CmsQuestionDtos.QuestionDetail;
import com.pps.smartenglish.exercise.repository.GrammarElementRepository;
import com.pps.smartenglish.exercise.repository.QuestionChoiceRepository;
import com.pps.smartenglish.exercise.repository.QuestionTagRepository;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.stereotype.Component;

/** Dựng chi tiết câu hỏi đầy đủ (có answerKey, isCorrect) cho CMS bằng 3 truy vấn cố định, không N+1. */
@Component
class QuestionDetails {

    private final QuestionChoiceRepository choices;
    private final QuestionTagRepository tags;
    private final GrammarElementRepository grammar;

    private final RegradeRequirement regrade;
    private final QuestionMediaService media;
    private final RichText rich;

    QuestionDetails(QuestionChoiceRepository choices, QuestionTagRepository tags, GrammarElementRepository grammar,
                    RegradeRequirement regrade, QuestionMediaService media, RichText rich) {
        this.rich = rich;
        this.choices = choices;
        this.tags = tags;
        this.grammar = grammar;
        this.regrade = regrade;
        this.media = media;
    }

    List<QuestionDetail> of(List<Question> questions) {
        if (questions.isEmpty()) {
            return List.of();
        }
        List<Long> ids = questions.stream().map(Question::getId).toList();
        Map<Long, List<QuestionChoice>> choiceMap = choices.findByQuestionIdInOrderByLabelAscIdAsc(ids).stream()
                .collect(Collectors.groupingBy(QuestionChoice::getQuestionId));
        Map<Long, GrammarElement> elements = grammar.findAll().stream().collect(Collectors.toMap(GrammarElement::getId, e -> e));
        Map<Long, List<String>> codeMap = tags.findByQuestionIdIn(ids).stream()
                .collect(Collectors.groupingBy(QuestionTag::getQuestionId, Collectors.collectingAndThen(Collectors.toList(),
                        l -> l.stream().map(t -> elements.get(t.getGrammarElementId()))
                                .sorted(Comparator.comparingInt(GrammarElement::getSortOrder).thenComparing(GrammarElement::getId))
                                .map(GrammarElement::getCode).toList())));
        Set<Long> required = regrade.among(questions);
        var mediaMap = media.forQuestions(ids);
        var html = rich.render(Stream.concat(questions.stream().map(Question::getPromptHtml),
                choiceMap.values().stream().flatMap(List::stream).map(QuestionChoice::getContentHtml)).toList());
        return questions.stream().map(q -> new QuestionDetail(q.getId(), q.getBankId(), q.getType().name(), q.getPrompt(),
                q.getPayload(), q.getAnswerKey(),
                choiceMap.getOrDefault(q.getId(), List.of()).stream()
                        .map(c -> new ChoiceView(c.getId(), c.getLabel(), c.getContent(), c.getContentHtml() == null ? null : html.get(c.getContentHtml()),
                                c.isCorrect(), c.getExplanation())).toList(),
                q.getExplanation(), q.getSortOrder(), codeMap.getOrDefault(q.getId(), List.of()), q.getAnswerVersion(),
                required.contains(q.getId()), q.getSkill(), q.getInstruction(),
                mediaMap.getOrDefault(q.getId(), List.of()), q.getGroupId(),
                q.getPromptHtml() == null ? null : html.get(q.getPromptHtml()))).toList();
    }
}
