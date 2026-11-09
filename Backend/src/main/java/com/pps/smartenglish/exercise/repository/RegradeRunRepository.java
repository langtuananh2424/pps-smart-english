package com.pps.smartenglish.exercise.repository;

import com.pps.smartenglish.exercise.domain.RegradeRun;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface RegradeRunRepository extends JpaRepository<RegradeRun, Long> {

    Optional<RegradeRun> findByQuestionIdAndQuestionVersion(Long questionId, int questionVersion);

    @Query("select r from RegradeRun r where r.id < :before order by r.id desc")
    List<RegradeRun> findPage(@Param("before") Long before, Pageable page);

    @Query("select r from RegradeRun r where r.questionId = :questionId and r.id < :before order by r.id desc")
    List<RegradeRun> findPageByQuestion(@Param("questionId") Long questionId, @Param("before") Long before, Pageable page);

    /** Câu có run RUNNING hoặc DONE ở đúng phiên bản đáp án hiện tại (để tính regradeRequired). */
    @Query("select r.questionId from RegradeRun r join Question q on q.id = r.questionId where r.questionId in :ids "
            + "and r.status in ('RUNNING', 'DONE') and r.questionVersion = q.answerVersion")
    List<Long> findQuestionsWithActiveRun(@Param("ids") Collection<Long> ids);

    @Transactional
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update RegradeRun r set r.total = :total where r.id = :id")
    int setTotal(@Param("id") Long id, @Param("total") int total);

    /**
     * Run có phiên bản khác answer_version hiện tại bị thay thế (Admin sửa tiếp giữa chừng). Kết quả đã áp vẫn giữ; changed và
     * failed chốt theo các item đã xử lý.
     */
    @Transactional
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "update regrade_runs set status = 'SUPERSEDED', finished_at = :now, "
            + "changed = (select count(*) from regrade_items i where i.run_id = regrade_runs.id and i.status = 'DONE' "
            + "and i.old_is_correct is distinct from i.new_is_correct), "
            + "failed = (select count(*) from regrade_items i where i.run_id = regrade_runs.id and i.status = 'FAILED') "
            + "where status = 'RUNNING' and question_version <> "
            + "(select q.answer_version from questions q where q.id = regrade_runs.question_id)", nativeQuery = true)
    int supersedeStale(@Param("now") Instant now);

    /** Run hết item PENDING/AI_WAITING thì DONE; điều kiện status = RUNNING nên nhiều instance chốt cũng chỉ một bên thắng. */
    @Transactional
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "update regrade_runs set status = 'DONE', finished_at = :now, "
            + "total = (select count(*) from regrade_items i where i.run_id = regrade_runs.id), "
            + "changed = (select count(*) from regrade_items i where i.run_id = regrade_runs.id and i.status = 'DONE' "
            + "and i.old_is_correct is distinct from i.new_is_correct), "
            + "failed = (select count(*) from regrade_items i where i.run_id = regrade_runs.id and i.status = 'FAILED') "
            + "where status = 'RUNNING' and not exists (select 1 from regrade_items i where i.run_id = regrade_runs.id "
            + "and i.status in ('PENDING', 'AI_WAITING'))", nativeQuery = true)
    int finishDrained(@Param("now") Instant now);

    /** Bấm lại run DONE có item lỗi: đưa về RUNNING để worker chạy lại phần lỗi. */
    @Transactional
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "update regrade_runs set status = 'RUNNING', finished_at = null where id = :id and status = 'DONE'",
            nativeQuery = true)
    int reopen(@Param("id") Long id);
}
