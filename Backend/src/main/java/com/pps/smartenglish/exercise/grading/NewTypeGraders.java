package com.pps.smartenglish.exercise.grading;

import static com.pps.smartenglish.exercise.domain.QuestionType.*;

import org.springframework.stereotype.Component;

/** D-154, D-157: mỗi kiểu mới một grader; chỉ khác nhau ở kiểu và trường đáp án, phần chấm dùng chung ở TextAnswerGrader và PickGrader. */
final class NewTypeGraders {

    private NewTypeGraders() {}

    // ---- gõ chữ: key {accepted}, answer {text} ----

    @Component
    static class Dictation extends TextAnswerGrader { Dictation() { super(DICTATION, true); } }

    @Component
    static class ListenForm extends TextAnswerGrader { ListenForm() { super(LISTEN_FORM, false); } }

    @Component
    static class Sentence extends TextAnswerGrader { Sentence() { super(IELTS_SENTENCE, false); } }

    @Component
    static class Summary extends TextAnswerGrader { Summary() { super(IELTS_SUMMARY, false); } }

    @Component
    static class Note extends TextAnswerGrader { Note() { super(IELTS_NOTE, false); } }

    @Component
    static class Table extends TextAnswerGrader { Table() { super(IELTS_TABLE, false); } }

    @Component
    static class Flowchart extends TextAnswerGrader { Flowchart() { super(IELTS_FLOWCHART, false); } }

    @Component
    static class Form extends TextAnswerGrader { Form() { super(IELTS_FORM, false); } }

    @Component
    static class Diagram extends TextAnswerGrader { Diagram() { super(IELTS_DIAGRAM, false); } }

    @Component
    static class Short extends TextAnswerGrader { Short() { super(IELTS_SHORT, false); } }

    // ---- chọn một giá trị ----

    @Component
    static class Tfng extends PickGrader { Tfng() { super(IELTS_TFNG, "value"); } }

    @Component
    static class Ynng extends PickGrader { Ynng() { super(IELTS_YNNG, "value"); } }

    @Component
    static class MatchInfo extends PickGrader { MatchInfo() { super(IELTS_MATCH_INFO, "option"); } }

    @Component
    static class MatchHeadings extends PickGrader { MatchHeadings() { super(IELTS_MATCH_HEADINGS, "option"); } }

    @Component
    static class MatchFeatures extends PickGrader { MatchFeatures() { super(IELTS_MATCH_FEATURES, "option"); } }

    @Component
    static class MatchEndings extends PickGrader { MatchEndings() { super(IELTS_MATCH_ENDINGS, "option"); } }

    @Component
    static class Matching extends PickGrader { Matching() { super(IELTS_MATCHING, "option"); } }

    @Component
    static class SummaryBank extends PickGrader { SummaryBank() { super(IELTS_SUMMARY_BANK, "word"); } }

    @Component
    static class MapLabel extends PickGrader { MapLabel() { super(IELTS_MAP, "option"); } }
}
