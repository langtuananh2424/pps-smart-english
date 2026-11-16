package com.pps.smartenglish.ai.controller;

import com.pps.smartenglish.ai.service.AiTaskWeights;
import jakarta.validation.constraints.NotNull;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** D-32: xem và sửa trọng số trừ lượt AI. Quyền product.manage do SecurityConfig kiểm. */
@RestController
@RequestMapping("/api/admin/ai-weights")
class AdminAiWeightController {

    record Weights(@NotNull Map<String, Integer> weights) {}

    private final AiTaskWeights weights;

    AdminAiWeightController(AiTaskWeights weights) {
        this.weights = weights;
    }

    @GetMapping
    Weights get() {
        return new Weights(weights.all());
    }

    @PutMapping
    Weights put(@RequestBody @jakarta.validation.Valid Weights req) {
        return new Weights(weights.update(req.weights()));
    }
}
