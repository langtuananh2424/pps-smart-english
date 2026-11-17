package com.pps.smartenglish.exercise.service;

import com.pps.smartenglish.exercise.dto.GrammarElementInfo;
import com.pps.smartenglish.exercise.repository.GrammarElementRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GrammarElementService {

    private final GrammarElementRepository elements;

    GrammarElementService(GrammarElementRepository elements) {
        this.elements = elements;
    }

    @Transactional(readOnly = true)
    public List<GrammarElementInfo> list() {
        return elements.findAllByOrderBySortOrderAscIdAsc().stream()
                .map(e -> new GrammarElementInfo(e.getCode(), e.getName(), e.getColorHex())).toList();
    }
}
