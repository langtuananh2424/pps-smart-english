package com.pps.smartenglish.exercise.repository;

import com.pps.smartenglish.exercise.domain.Question;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface QuestionRepository extends JpaRepository<Question, Long> {

    List<Question> findByBankIdOrderBySortOrderAscIdAsc(Long bankId);

    /** Khóa dòng câu hỏi để đếm + chèn gợi ý của cùng một câu chạy tuần tự. Rỗng nếu không có câu. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select q.id from Question q where q.id = :id")
    List<Long> lockById(@Param("id") Long id);

    /** Id câu WRITING của các bộ, theo thứ tự bộ rồi thứ tự câu. */
    @Query("select q.id from Question q where q.bankId in :bankIds and q.type = 'WRITING' order by q.bankId, q.sortOrder, q.id")
    List<Long> findWritingIds(@Param("bankIds") Collection<Long> bankIds);

    boolean existsByBankId(Long bankId);

    boolean existsByGroupId(Long groupId);

    long countByBankId(Long bankId);

    /** [bankId, số câu] cho nhiều bộ trong một truy vấn (danh sách CMS). */
    @Query("select q.bankId, count(q) from Question q where q.bankId in :bankIds group by q.bankId")
    List<Object[]> countByBankIds(@Param("bankIds") Collection<Long> bankIds);
}
