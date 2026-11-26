package com.pps.smartenglish.exercise.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Random;

/** D-152: chọn câu, ưu tiên câu sai nhiều (cùng số lần sai thì ngẫu nhiên), còn lại ngẫu nhiên; thứ tự làm bài xáo ngẫu nhiên. */
final class ExtraPicker {

    private ExtraPicker() {}

    static List<Long> pick(List<Long> candidates, Map<Long, Long> wrong, int size, Random random) {
        var shuffled = new ArrayList<>(candidates);
        java.util.Collections.shuffle(shuffled, random);
        shuffled.sort(Comparator.comparingLong((Long id) -> wrong.getOrDefault(id, 0L)).reversed()); // sort ổn định giữ thứ tự xáo
        var chosen = new ArrayList<>(shuffled.subList(0, Math.min(size, shuffled.size())));
        java.util.Collections.shuffle(chosen, random);
        return chosen;
    }
}
