package com.pps.smartenglish.shared.persistence;

import com.pps.smartenglish.shared.error.ApiException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * Ghi có flush ngay trong service để lỗi ràng buộc (unique, khóa ngoại) lộ ra tại đây thành 409 thay vì 500 lúc commit.
 * Lỗi ném ra từ phương thức @Transactional nên transaction rollback; PostgreSQL không cho dùng tiếp transaction đã lỗi.
 * Dùng chung cho CMS của catalog và exercise.
 */
@Component
public class CmsWrites {

    /** conflictCode: slug_taken, level_code_taken... Race giữa hai request cùng slug/mã cũng rơi vào đây. */
    public <T> T save(JpaRepository<T, Long> repo, T entity, String conflictCode, String conflictMessage) {
        try {
            return repo.saveAndFlush(entity);
        } catch (DataIntegrityViolationException e) {
            throw new ApiException(HttpStatus.CONFLICT, conflictCode, conflictMessage);
        }
    }

    /** Xóa cứng; còn dòng khác tham chiếu (tiến độ, bài nộp, enrollment, gói bán...) thì 409 in_use, không xóa cascade. */
    public <T> void delete(JpaRepository<T, Long> repo, T entity) {
        try {
            repo.delete(entity);
            repo.flush();
        } catch (DataIntegrityViolationException e) {
            throw inUse();
        }
    }

    public static ApiException inUse() {
        return new ApiException(HttpStatus.CONFLICT, "in_use", "Còn nội dung hoặc dữ liệu khác đang tham chiếu tới mục này");
    }
}
