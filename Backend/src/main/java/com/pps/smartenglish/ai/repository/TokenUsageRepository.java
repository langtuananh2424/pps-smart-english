package com.pps.smartenglish.ai.repository;

import com.pps.smartenglish.ai.domain.TokenUsage;
import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TokenUsageRepository extends JpaRepository<TokenUsage, Long> {

    /**
     * Chi phí theo loại job trong [start, end): [kind, số job khác nhau, chi phí USD, input, output, cache read, cache write].
     * Một job thử lại nhiều lần có nhiều dòng usage nên jobs đếm DISTINCT.
     */
    @Query(nativeQuery = true, value = """
            SELECT j.kind, COUNT(DISTINCT u.job_id), SUM(u.cost_usd), SUM(u.input_tokens), SUM(u.output_tokens),
                   SUM(u.cache_read_tokens), SUM(u.cache_write_tokens)
            FROM ai_grading_token_usage u JOIN ai_jobs j ON j.id = u.job_id
            WHERE u.created_at >= :start AND u.created_at < :end
            GROUP BY j.kind ORDER BY j.kind
            """)
    List<Object[]> usageByKind(@Param("start") Instant start, @Param("end") Instant end);
}
