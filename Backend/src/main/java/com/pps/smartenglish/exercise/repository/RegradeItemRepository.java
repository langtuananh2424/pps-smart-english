package com.pps.smartenglish.exercise.repository;

import com.pps.smartenglish.exercise.domain.RegradeItem;
import com.pps.smartenglish.exercise.dto.RegradeItemView;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

/** Mọi chuyển trạng thái item là UPDATE có điều kiện status (native, vì cột status không nằm trong entity ghi). */
public interface RegradeItemRepository extends JpaRepository<RegradeItem, Long> {

    /** Một câu INSERT ... SELECT: mọi câu trả lời của lượt đã nộp. old_is_correct là giá trị lúc bấm xác nhận. */
    @Transactional
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "insert into regrade_items (run_id, answer_id, attempt_id, status, old_is_correct) "
            + "select :runId, a.id, a.attempt_id, 'PENDING', a.is_correct from student_answers a "
            + "join student_exercise_attempts t on t.id = a.attempt_id where a.question_id = :questionId and t.submitted_at is not null",
            nativeQuery = true)
    int insertFromAnswers(@Param("runId") Long runId, @Param("questionId") Long questionId);

    /** Khóa đúng một item đang PENDING; đã có instance khác giữ hoặc đã xử lý thì rỗng. */
    @Query(value = "select id from regrade_items where id = :id and status = 'PENDING' for update skip locked", nativeQuery = true)
    List<Long> lockPending(@Param("id") Long id);

    /** Ứng viên cho tick: item PENDING của run đang chạy. Chưa khóa; khóa từng item khi xử lý. */
    @Query("select i.id from RegradeItem i where i.status = 'PENDING' "
            + "and i.runId in (select r.id from RegradeRun r where r.status = 'RUNNING') order by i.id")
    List<Long> pendingIds(Pageable page);

    /** [id, jobId] của item đang chờ AI, để hòa giải với trạng thái job. */
    @Query("select i.id, i.jobId from RegradeItem i where i.status = 'AI_WAITING' order by i.id")
    List<Object[]> waiting(Pageable page);

    @Transactional
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "update regrade_items set status = :to where id = :id and status = :from", nativeQuery = true)
    int transition(@Param("id") Long id, @Param("from") String from, @Param("to") String to);

    @Transactional
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "update regrade_items set status = 'AI_WAITING', job_id = :jobId where id = :id and status = 'PENDING'",
            nativeQuery = true)
    int markWaiting(@Param("id") Long id, @Param("jobId") Long jobId);

    @Transactional
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "update regrade_items set status = 'FAILED', processed_at = :now where id = :id and status = 'AI_WAITING'",
            nativeQuery = true)
    int markFailed(@Param("id") Long id, @Param("now") Instant now);

    /** Item chưa xong của run đã bị thay thế thì hủy. */
    @Transactional
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "update regrade_items set status = 'CANCELLED' where status in ('PENDING', 'AI_WAITING') "
            + "and run_id in (select id from regrade_runs where status = 'SUPERSEDED')", nativeQuery = true)
    int cancelOfSuperseded();

    /** Bấm xác nhận lại run có lỗi: chỉ phần FAILED chạy lại. */
    @Transactional
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "update regrade_items set status = 'PENDING', job_id = null, processed_at = null "
            + "where run_id = :runId and status = 'FAILED'", nativeQuery = true)
    int retryFailed(@Param("runId") Long runId);

    /** [status, số lượng] của một run. */
    @Query("select i.status, count(i) from RegradeItem i where i.runId = :runId group by i.status")
    List<Object[]> countByStatus(@Param("runId") Long runId);

    @Query("select new com.pps.smartenglish.exercise.dto.RegradeItemView(i.id, i.attemptId, t.userId, i.answerId, i.oldCorrect, "
            + "i.newCorrect, i.oldAttemptScore, i.newAttemptScore, i.status) from RegradeItem i, StudentAttempt t "
            + "where t.id = i.attemptId and i.runId = :runId and i.id < :before order by i.id desc")
    List<RegradeItemView> findPage(@Param("runId") Long runId, @Param("before") Long before, Pageable page);
}
