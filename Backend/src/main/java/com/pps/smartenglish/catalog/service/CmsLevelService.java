package com.pps.smartenglish.catalog.service;

import com.pps.smartenglish.catalog.domain.ContentStatus;
import com.pps.smartenglish.catalog.domain.CourseLevel;
import com.pps.smartenglish.catalog.dto.CmsDtos.CreateLevel;
import com.pps.smartenglish.catalog.dto.CmsDtos.LevelView;
import com.pps.smartenglish.catalog.dto.CmsDtos.PatchLevel;
import com.pps.smartenglish.catalog.repository.CourseLevelRepository;
import com.pps.smartenglish.catalog.repository.CourseRepository;
import com.pps.smartenglish.catalog.repository.TopicRepository;
import com.pps.smartenglish.shared.error.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** CMS cho Level. course_id không đổi sau khi tạo. */
@Service
public class CmsLevelService {

    private static final String CODE_TAKEN = "level_code_taken";

    private final CourseLevelRepository levels;
    private final CourseRepository courses;
    private final TopicRepository topics;
    private final CmsWrites writes;

    CmsLevelService(CourseLevelRepository levels, CourseRepository courses, TopicRepository topics, CmsWrites writes) {
        this.levels = levels;
        this.courses = courses;
        this.topics = topics;
        this.writes = writes;
    }

    @Transactional
    public LevelView create(long courseId, CreateLevel req) {
        if (!courses.existsById(courseId)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "course_not_found", "Không tìm thấy khóa học");
        }
        String code = req.code().strip();
        if (levels.existsByCourseIdAndCode(courseId, code)) {
            throw taken();
        }
        return view(writes.save(levels, new CourseLevel(courseId, code, req.title().strip(),
                req.sortOrder() == null ? 0 : req.sortOrder()), CODE_TAKEN, "Mã level đã được dùng trong khóa này"));
    }

    @Transactional
    public LevelView patch(long id, PatchLevel req) {
        CourseLevel level = find(id);
        if (req.code() != null) {
            String code = req.code().strip();
            if (levels.existsByCourseIdAndCodeAndIdNot(level.getCourseId(), code, id)) {
                throw taken();
            }
            level.setCode(code);
        }
        if (req.title() != null) {
            level.setTitle(req.title().strip());
        }
        if (req.sortOrder() != null) {
            level.setSortOrder(req.sortOrder());
        }
        return view(writes.save(levels, level, CODE_TAKEN, "Mã level đã được dùng trong khóa này"));
    }

    /** Còn topic, gói bán, enrollment... tham chiếu thì in_use. */
    @Transactional
    public void delete(long id) {
        CourseLevel level = find(id);
        if (topics.existsByLevelId(id)) {
            throw CmsWrites.inUse();
        }
        writes.delete(levels, level);
    }

    /** Idempotent. Xuất bản level không tự xuất bản topic con; level DRAFT thì topic con có PUBLISHED cũng chưa hiện. */
    @Transactional
    public LevelView setPublished(long id, boolean published) {
        CourseLevel level = find(id);
        level.setStatus(published ? ContentStatus.PUBLISHED : ContentStatus.DRAFT);
        return view(writes.save(levels, level, CODE_TAKEN, "Mã level đã được dùng trong khóa này"));
    }

    private CourseLevel find(long id) {
        return levels.findById(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "level_not_found", "Không tìm thấy level"));
    }

    private static ApiException taken() {
        return new ApiException(HttpStatus.CONFLICT, CODE_TAKEN, "Mã level đã được dùng trong khóa này");
    }

    static LevelView view(CourseLevel l) {
        return new LevelView(l.getId(), l.getCourseId(), l.getCode(), l.getTitle(), l.getSortOrder(), l.getStatus());
    }
}
