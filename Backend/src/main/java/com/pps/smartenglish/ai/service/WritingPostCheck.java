package com.pps.smartenglish.ai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.pps.smartenglish.ai.dto.WritingGrade;
import com.pps.smartenglish.ai.dto.WritingGrade.Mark;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Hậu kiểm output của model: không tin điểm tổng, và tự dựng markedAnswer từ bài gốc theo danh sách lỗi (marks). */
@Component
public class WritingPostCheck {

    static final Set<String> CODES = Set.of("ok", "sp1", "sp2", "gr1", "gr2", "wd1", "wd2", "pu1", "pu2");
    private static final Logger log = LoggerFactory.getLogger(WritingPostCheck.class);

    private final ObjectMapper mapper;

    WritingPostCheck(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    private record Span(int start, int end, String code) {

        boolean overlaps(int s, int e) {
            return s < end && start < e;
        }
    }

    /** Kẹp 4 điểm tiêu chí về 0..100, tự tính scorePercent, dựng markedAnswer; kết quả không có trường marks. */
    public ObjectNode apply(WritingGrade g, String original, int wordCount, int copiedWords) {
        int tr = clamp(g.taskResponse());
        int cc = clamp(g.coherence());
        int lr = clamp(g.lexical());
        int gr = clamp(g.grammar());

        ObjectNode out = mapper.valueToTree(new WritingGrade(tr, cc, lr, gr, null, g.correctedAnswer(), g.connectives(),
                g.grammarElements(), g.gateNote(), g.feedback()));
        out.remove("marks");
        out.put("markedAnswer", markedAnswer(original, g.marks()));
        out.put("scorePercent", scorePercent(tr, cc, lr, gr));
        out.put("wordCount", wordCount);
        out.put("copiedWords", copiedWords);
        return out;
    }

    /** Trung bình 4 tiêu chí, làm tròn xuống bội số của 5. */
    static int scorePercent(int a, int b, int c, int d) {
        return (int) ((a + b + c + d) / 4.0 / 5) * 5;
    }

    /**
     * Mỗi mục tìm quote (khớp đúng ký tự) từ sau mục vừa đánh dấu; không thấy thì tìm từ đầu bài, nhưng không được
     * chồng lên đoạn đã đánh dấu. Mục sai code, quote rỗng hoặc không đặt được bị bỏ. Bỏ token đi phải ra đúng bài gốc.
     */
    String markedAnswer(String original, List<Mark> marks) {
        List<Span> placed = new ArrayList<>();
        int cursor = 0;
        int dropped = 0;
        for (Mark m : marks == null ? List.<Mark>of() : marks) {
            if (m == null || m.code() == null || !CODES.contains(m.code()) || m.quote() == null || m.quote().isEmpty()) {
                dropped++;
                continue;
            }
            int at = find(original, m.quote(), cursor, placed);
            if (at < 0) {
                at = find(original, m.quote(), 0, placed);
            }
            if (at < 0) {
                dropped++;
                continue;
            }
            placed.add(new Span(at, at + m.quote().length(), m.code()));
            cursor = at + m.quote().length();
        }
        if (dropped > 0) {
            log.warn("Bỏ {} mục đánh dấu lỗi không đặt được vào bài gốc", dropped);
        }
        placed.sort(Comparator.comparingInt(Span::start));
        StringBuilder sb = new StringBuilder();
        int pos = 0;
        for (Span s : placed) {
            sb.append(original, pos, s.start()).append("{{").append(s.code()).append('|')
                    .append(original, s.start(), s.end()).append("}}");
            pos = s.end();
        }
        return sb.append(original, pos, original.length()).toString();
    }

    private static int find(String text, String quote, int from, List<Span> placed) {
        int at = text.indexOf(quote, from);
        while (at >= 0) {
            int s = at;
            int e = at + quote.length();
            if (placed.stream().noneMatch(p -> p.overlaps(s, e))) {
                return at;
            }
            at = text.indexOf(quote, at + 1);
        }
        return -1;
    }

    private static int clamp(int v) {
        return Math.max(0, Math.min(100, v));
    }
}
