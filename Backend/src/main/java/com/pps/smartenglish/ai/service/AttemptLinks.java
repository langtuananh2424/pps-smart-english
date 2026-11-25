package com.pps.smartenglish.ai.service;

/** D-145: ai cần biết lượt làm bài hợp lệ để gắn phiên Viết/Nói vào; exercise cài đặt để ai không phụ thuộc ngược. */
public interface AttemptLinks {

    /**
     * Lượt phải của đúng học viên (không thì 404 attempt_not_found, không lộ lượt người khác), còn đang mở (không thì 409
     * attempt_already_submitted) và bộ của lượt phải chứa câu này (không thì 400). Từ chối thay vì lặng lẽ không gắn:
     * không gắn nghĩa là bài đã làm không được tính điểm mà học viên không biết.
     */
    void requireOpen(long userId, long attemptId, long questionId);

    /** D-147: lượt còn mở. attemptId null (phiên luyện tập) hoặc lượt không tồn tại thì false. */
    boolean isOpen(Long attemptId);

    /** D-147: phiên gắn lượt đã nộp thì khóa: 409 attempt_already_submitted. attemptId null thì bỏ qua. */
    void requireAttemptOpen(Long attemptId);

    /**
     * D-147: khóa dòng lượt đến hết transaction rồi kiểm còn mở (không thì 409). Gọi trước khi trừ lượt AI trong cùng transaction:
     * lượt đang tự nộp thì chờ rồi 409 và không trừ lượt. attemptId null thì bỏ qua.
     */
    void requireOpenLocked(Long attemptId);

    /**
     * D-147: id lượt chưa nộp của học viên trên bộ chứa câu này, hoặc null. Phiên luyện riêng (không gắn lượt) của câu như vậy
     * bị chặn lúc tạo và bị khóa kết quả lúc đọc, nếu không thì xem trước bản sửa AI rồi chép vào lượt đang mở.
     */
    Long openAttemptForQuestion(long userId, long questionId);

    /** D-147: như trên nhưng ném 409 question_in_open_attempt kèm attemptId. */
    void requireNotInOpenAttempt(long userId, long questionId);

    /** D-130: lượt thuộc một bài giao (bộ bài): phiên Viết/Nói gắn lượt không trừ lượt AI. attemptId null thì false. */
    boolean isAssigned(Long attemptId);

    /**
     * D-147: khóa dòng lượt (mở hay đã nộp) đến hết transaction. Đường hoàn lượt AI của job có lượt phải gọi trước khi hoàn: mọi
     * đường khóa theo thứ tự "lượt trước, quota sau" nên không deadlock với nộp chấm và nộp lượt. attemptId null thì bỏ qua.
     */
    void lockAttempt(Long attemptId);

    /** Chỉ kiểm lượt thuộc đúng người gọi (lượt người khác: 404 attempt_not_found), đã nộp hay chưa đều được. */
    void requireOwned(long userId, long attemptId);
}
