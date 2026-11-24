package com.pps.smartenglish.exercise.bulk;

import com.pps.smartenglish.exercise.domain.Question;
import com.pps.smartenglish.exercise.domain.QuestionBank;
import com.pps.smartenglish.exercise.dto.ImportDtos.Duplicate;
import com.pps.smartenglish.exercise.repository.QuestionBankRepository;
import com.pps.smartenglish.exercise.repository.QuestionRepository;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

/**
 * Gợi ý câu nghi trùng (D-133): cùng dấu vân tay (PromptHash) trong cùng lần nhập, trong cùng bộ, hoặc trong bộ khác cùng topic.
 * Chỉ là cảnh báo, không chặn. So khớp mờ (gần giống) để sau.
 */
@Component
class DuplicateFinder {

    /** Mỗi dòng chỉ báo tối đa chừng này câu trùng để phản hồi gọn. */
    private static final int PER_ROW = 3;

    record Candidate(int index, int line, String hash) {}

    private final QuestionRepository questions;
    private final QuestionBankRepository banks;
    private final ImportProperties props;

    DuplicateFinder(QuestionRepository questions, QuestionBankRepository banks, ImportProperties props) {
        this.questions = questions;
        this.banks = banks;
        this.props = props;
    }

    /** Khóa kết quả là Candidate.index; dòng không trùng thì không có khóa. Có thể điền dấu vân tay cho câu cũ (ghi DB). */
    Map<Integer, List<Duplicate>> find(QuestionBank bank, List<Candidate> rows) {
        Map<Integer, List<Duplicate>> out = new HashMap<>();
        if (rows.isEmpty()) {
            return out;
        }
        Map<String, Integer> firstLine = new HashMap<>();
        for (Candidate c : rows) {
            Integer first = firstLine.putIfAbsent(c.hash(), c.line());
            if (first != null) {
                out.computeIfAbsent(c.index(), k -> new ArrayList<>()).add(new Duplicate("FILE", null, bank.getId(), null, first));
            }
        }
        List<Long> bankIds = bank.getTopicId() == null ? List.of(bank.getId())
                : banks.findByTopicIdOrderByIdAsc(bank.getTopicId()).stream().map(QuestionBank::getId).toList();
        fillMissingHashes(bankIds);
        Map<String, List<Question>> existing = questions.findByHashes(bankIds, firstLine.keySet()).stream()
                .collect(Collectors.groupingBy(Question::getPromptHash, LinkedHashMap::new, Collectors.toList()));
        if (existing.isEmpty()) {
            return out;
        }
        Map<Long, String> titles = banks.findAllById(bankIds).stream().collect(Collectors.toMap(QuestionBank::getId, QuestionBank::getTitle));
        for (Candidate c : rows) {
            existing.getOrDefault(c.hash(), List.of()).stream()
                    .sorted(java.util.Comparator.comparing((Question q) -> !q.getBankId().equals(bank.getId())))
                    .limit(PER_ROW)
                    .forEach(q -> out.computeIfAbsent(c.index(), k -> new ArrayList<>()).add(new Duplicate(
                            q.getBankId().equals(bank.getId()) ? "BANK" : "TOPIC", q.getId(), q.getBankId(), titles.get(q.getBankId()), null)));
        }
        return out;
    }

    /** Câu soạn trước #70 chưa có dấu vân tay: điền dần theo lần so trùng (tối đa maxHashFill câu mỗi lần). */
    private void fillMissingHashes(List<Long> bankIds) {
        List<Question> missing = questions.findUnhashed(bankIds, PageRequest.of(0, props.maxHashFill()));
        if (!missing.isEmpty()) {
            missing.forEach(Question::refreshHash);
            questions.saveAll(missing);
        }
    }
}
