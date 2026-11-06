package com.pps.smartenglish.catalog.service;

import com.pps.smartenglish.shared.error.ApiException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * Ghi có flush ngay trong service để lỗi ràng buộc (unique, khóa ngoại) lộ ra tại đây thành 409 thay vì 500 lúc commit.
 * Lỗi ném ra từ phương thức @Transactional nên transaction rollback; PostgreSQL không cho dùng tiếp transaction đã lỗi.
 */
@Component
class CmsWrites {

    /** conflictCode: slug_taken, level_code_taken... Race giữa hai request cùng slug/mã cũng rơi vào đây. */
    <T> T save(JpaRepository<T, Long> repo, T entity, String conflictCode, String conflictMessage) {
        try {
            return repo.saveAndFlush(entity);
        } catch (DataIntegrityViolationException e) {
            throw new ApiException(HttpStatus.CONFLICT, conflictCode, conflictMessage);
        }
    }

    /** Xóa cứng; còn dòng khác tham chiếu (tiến độ, enrollment, gói bán, bộ câu hỏi...) thì 409 in_use, không xóa cascade. */
    <T> void delete(JpaRepository<T, Long> repo, T entity) {
        try {
            repo.delete(entity);
            repo.flush();
        } catch (DataIntegrityViolationException e) {
            throw inUse();
        }
    }

    static ApiException inUse() {
        return new ApiException(HttpStatus.CONFLICT, "in_use", "Còn nội dung hoặc dữ liệu khác đang tham chiếu tới mục này");
    }
}
