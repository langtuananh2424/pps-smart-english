package com.pps.smartenglish.lms.service;

import com.pps.smartenglish.catalog.repository.CourseRepository;
import com.pps.smartenglish.lms.dto.CourseProgress;
import com.pps.smartenglish.lms.repository.LessonProgressRepository;
import com.pps.smartenglish.shared.error.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** D-46: tỷ lệ học xong khóa = bài đã hoàn thành (xem đủ ngưỡng của khóa, courses.lesson_complete_percent, mặc định 80) / bài đã xuất bản. */
@Service
public class CourseProgressService {

    private final LessonProgressRepository progress;
    private final CourseRepository courses;

    CourseProgressService(LessonProgressRepository progress, CourseRepository courses) {
        this.progress = progress;
        this.courses = courses;
    }

    /** Khóa không tồn tại thì 404. */
    @Transactional(readOnly = true)
    public CourseProgress of(long userId, long courseId) {
        if (!courses.existsById(courseId)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "course_not_found", "Không tìm thấy khóa học");
        }
        return compute(userId, courseId);
    }

    /** Không kiểm tra khóa tồn tại: dành cho người gọi đã có khóa trong tay. */
    @Transactional(readOnly = true)
    public CourseProgress compute(long userId, long courseId) {
        long total = progress.countVisibleLessons(courseId);
        long done = progress.countCompletedVisible(userId, courseId);
        // Khóa chưa có bài nào thì không có gì để học: coi là 100%, không chặn bài thi.
        return new CourseProgress(done, total, total == 0 ? 100 : (int) (done * 100 / total));
    }
}
