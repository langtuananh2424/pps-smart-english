package com.pps.smartenglish.ai.service;

import com.pps.smartenglish.ai.domain.AiTask;
import com.pps.smartenglish.ai.domain.AiTaskWeight;
import com.pps.smartenglish.ai.repository.AiTaskWeightRepository;
import com.pps.smartenglish.shared.audit.AuditRecorder;
import com.pps.smartenglish.shared.error.ApiException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Trọng số trừ lượt theo tác vụ (D-32): đọc DB mỗi lần để quản trị đổi là áp dụng ngay. */
@Service
public class AiTaskWeights {

    public static final int MAX_WEIGHT = 20;

    private final AiTaskWeightRepository weights;
    private final AuditRecorder audit;

    AiTaskWeights(AiTaskWeightRepository weights, AuditRecorder audit) {
        this.weights = weights;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public int of(AiTask task) {
        return weights.findById(task.name()).map(AiTaskWeight::getWeight)
                .orElseThrow(() -> new IllegalStateException("Thiếu trọng số " + task));
    }

    @Transactional(readOnly = true)
    public Map<String, Integer> all() {
        var out = new TreeMap<String, Integer>();
        weights.findAll().forEach(w -> out.put(w.getCode(), w.getWeight()));
        return out;
    }

    /** Chỉ sửa các mã gửi lên; mã lạ hoặc weight ngoài 1..20 thì 400 và không sửa gì. */
    @Transactional
    public Map<String, Integer> update(Map<String, Integer> req) {
        var before = all();
        var errors = new LinkedHashMap<String, String>();
        req.forEach((code, w) -> {
            if (!before.containsKey(code)) {
                errors.put(code, "Mã tác vụ không tồn tại");
            } else if (w == null || w < 1 || w > MAX_WEIGHT) {
                errors.put(code, "Phải từ 1 đến " + MAX_WEIGHT);
            }
        });
        if (!errors.isEmpty()) {
            var e = errors.entrySet().iterator().next();
            throw ApiException.validation("weights." + e.getKey(), e.getValue());
        }
        req.forEach((code, w) -> weights.findById(code).ifPresent(row -> row.setWeight(w)));
        var after = all();
        audit.record("UPDATE", "AI_TASK_WEIGHTS", "all", before, after);
        return after;
    }
}
