package com.pps.smartenglish.exercise.repository;

import com.pps.smartenglish.exercise.domain.Question;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface QuestionRepository extends JpaRepository<Question, Long> {

    /** Mọi câu của bộ, kể cả câu đã lưu trữ (dùng cho lịch sử làm bài). */
    List<Question> findByBankIdOrderBySortOrderAscIdAsc(Long bankId);

    /** Câu còn hiệu lực: đường cho lượt làm mới và màn soạn (D-138: câu archived_at bị ẩn). */
    @Query("select q from Question q where q.bankId = :bankId and q.archivedAt is null order by q.sortOrder, q.id")
    List<Question> findActiveByBankId(@Param("bankId") Long bankId);

    /** Khóa dòng câu hỏi để đếm + chèn gợi ý của cùng một câu chạy tuần tự. Rỗng nếu không có câu. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select q.id from Question q where q.id = :id")
    List<Long> lockById(@Param("id") Long id);

    /** Id câu WRITING của các bộ, theo thứ tự bộ rồi thứ tự câu. */
    @Query("select q.id from Question q where q.bankId in :bankIds and q.type = 'WRITING' and q.archivedAt is null "
            + "order by q.bankId, q.sortOrder, q.id")
    List<Long> findWritingIds(@Param("bankIds") Collection<Long> bankIds);

    /** Kể cả câu lưu trữ: còn dòng nào thì bộ chưa xóa được. */
    boolean existsByBankId(Long bankId);

    boolean existsByGroupId(Long groupId);

    /** Chỉ đếm câu còn hiệu lực (chưa lưu trữ). */
    @Query("select count(q) from Question q where q.bankId = :bankId and q.archivedAt is null")
    long countByBankId(@Param("bankId") Long bankId);

    /** [bankId, số câu còn hiệu lực] cho nhiều bộ trong một truy vấn (danh sách CMS). */
    @Query("select q.bankId, count(q) from Question q where q.bankId in :bankIds and q.archivedAt is null group by q.bankId")
    List<Object[]> countByBankIds(@Param("bankIds") Collection<Long> bankIds);

    /** Thứ tự lớn nhất đang dùng trong bộ (0 nếu rỗng): lô nhập nối tiếp phía sau. */
    @Query("select coalesce(max(q.sortOrder), 0) from Question q where q.bankId = :bankId")
    int maxSortOrder(@Param("bankId") Long bankId);

    List<Question> findByImportBatchId(Long batchId);

    /** Câu còn hiệu lực chưa có dấu vân tay (soạn trước #70): điền lười khi so trùng. */
    @Query("select q from Question q where q.bankId in :bankIds and q.promptHash is null and q.archivedAt is null order by q.id")
    List<Question> findUnhashed(@Param("bankIds") Collection<Long> bankIds, Pageable page);

    @Query("select q from Question q where q.bankId in :bankIds and q.promptHash in :hashes and q.archivedAt is null order by q.id")
    List<Question> findByHashes(@Param("bankIds") Collection<Long> bankIds, @Param("hashes") Collection<String> hashes);

    /**
     * Câu của lô đã có người dùng tham chiếu (lượt làm, phiên viết/nói, bài giao, lần chấm lại): xóa sẽ mất lịch sử nên
     * hoàn tác chỉ ẩn những câu này. Liệt kê mọi bảng có khóa ngoại tới questions (trừ bảng con xóa cascade).
     */
    @Query(value = """
            select q.id from questions q where q.import_batch_id = :batchId and (
                exists (select 1 from student_answers a where a.question_id = q.id)
                or exists (select 1 from writing_sessions w where w.question_id = q.id)
                or exists (select 1 from speaking_submissions s where s.question_id = q.id)
                or exists (select 1 from assignments x where x.question_id = q.id)
                or exists (select 1 from regrade_runs r where r.question_id = q.id))
            """, nativeQuery = true)
    List<Long> findUsedIdsInBatch(@Param("batchId") Long batchId);
}
