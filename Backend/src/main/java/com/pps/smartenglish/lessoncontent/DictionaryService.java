package com.pps.smartenglish.lessoncontent;

import com.pps.smartenglish.lessoncontent.LessonContentDtos.DictionarySuggestion;
import com.pps.smartenglish.shared.error.ApiException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** D-175: gợi ý nghĩa từ bảng từ điển (nhập từ Wiktionary) cho CMS glossary. */
@Service
public class DictionaryService {

    static final int LIMIT = 10;
    private static final int MAX_QUERY = 100;

    private final DictionaryRepository dictionary;

    DictionaryService(DictionaryRepository dictionary) {
        this.dictionary = dictionary;
    }

    /** Khớp chính xác trước, rồi tiền tố (ngắn trước); không phân biệt hoa thường. */
    @Transactional(readOnly = true)
    public List<DictionarySuggestion> suggest(String q) {
        String key = q == null ? "" : q.strip().toLowerCase(Locale.ROOT);
        if (key.isEmpty() || key.length() > MAX_QUERY) {
            throw ApiException.validation("q", "Từ khóa từ 1 đến " + MAX_QUERY + " ký tự");
        }
        var found = new ArrayList<>(dictionary.findByWordKeyOrderByIdAsc(key, PageRequest.of(0, LIMIT)));
        if (found.size() < LIMIT) {
            String escaped = key.replace("!", "!!").replace("%", "!%").replace("_", "!_");
            found.addAll(dictionary.findByPrefix(escaped, key, PageRequest.of(0, LIMIT - found.size())));
        }
        return found.stream().map(d -> new DictionarySuggestion(d.getWord(), d.getPartOfSpeech(), d.getMeaningVi(), d.getExample())).toList();
    }
}
