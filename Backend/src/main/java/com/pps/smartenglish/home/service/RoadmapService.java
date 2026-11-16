package com.pps.smartenglish.home.service;

import com.pps.smartenglish.catalog.domain.CourseLevel;
import com.pps.smartenglish.catalog.domain.Lesson;
import com.pps.smartenglish.catalog.domain.Topic;
import com.pps.smartenglish.catalog.repository.CourseLevelRepository;
import com.pps.smartenglish.catalog.repository.CourseRepository;
import com.pps.smartenglish.catalog.repository.LessonRepository;
import com.pps.smartenglish.catalog.repository.TopicRepository;
import com.pps.smartenglish.exercise.domain.QuestionBank;
import com.pps.smartenglish.exercise.repository.QuestionBankRepository;
import com.pps.smartenglish.exercise.repository.QuestionRepository;
import com.pps.smartenglish.home.dto.HomeDtos.BankNode;
import com.pps.smartenglish.home.dto.HomeDtos.LessonNode;
import com.pps.smartenglish.home.dto.HomeDtos.LevelNode;
import com.pps.smartenglish.home.dto.HomeDtos.Resume;
import com.pps.smartenglish.home.dto.HomeDtos.Roadmap;
import com.pps.smartenglish.home.dto.HomeDtos.TopicNode;
import com.pps.smartenglish.lms.domain.LessonProgress;
import com.pps.smartenglish.lms.dto.PlayableLesson;
import com.pps.smartenglish.lms.repository.LessonProgressRepository;
import com.pps.smartenglish.lms.service.LessonAccessPolicy;
import com.pps.smartenglish.retention.domain.XpEvent;
import com.pps.smartenglish.retention.repository.XpEventRepository;
import com.pps.smartenglish.shared.error.ApiException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * GET /api/me/courses/{id}/roadmap: cây khóa kèm tiến độ, XP và bộ bài của riêng người dùng (D-51, D-52).
 * Số truy vấn cố định (không N+1). Quyền xem hỏi lại các LessonAccessPolicy hiện có, không viết lại quy tắc.
 */
@Service
public class RoadmapService {

    private static final String PUBLISHED = "PUBLISHED";
    private static final List<String> BANK_XP_SOURCES = List.of("EXERCISE", "EXERCISE_HIGH");

    private final CourseRepository courses;
    private final CourseLevelRepository levels;
    private final TopicRepository topics;
    private final LessonRepository lessons;
    private final LessonProgressRepository progress;
    private final QuestionBankRepository banks;
    private final QuestionRepository questions;
    private final XpEventRepository xpEvents;
    private final List<LessonAccessPolicy> policies;

    RoadmapService(CourseRepository courses, CourseLevelRepository levels, TopicRepository topics, LessonRepository lessons,
                   LessonProgressRepository progress, QuestionBankRepository banks, QuestionRepository questions,
                   XpEventRepository xpEvents, List<LessonAccessPolicy> policies) {
        this.courses = courses;
        this.levels = levels;
        this.topics = topics;
        this.lessons = lessons;
        this.progress = progress;
        this.banks = banks;
        this.questions = questions;
        this.xpEvents = xpEvents;
        this.policies = policies;
    }

    @Transactional(readOnly = true)
    public Roadmap of(long userId, long courseId) {
        courses.findById(courseId).filter(c -> PUBLISHED.equals(c.getStatus()))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "course_not_found", "Không tìm thấy khóa học"));
        // chỉ level, topic, bài PUBLISHED; cha DRAFT thì con không có trong cây (cùng quy tắc CatalogService)
        List<CourseLevel> lvls = levels.findByCourseIdAndStatusOrderBySortOrderAscIdAsc(courseId, PUBLISHED);
        List<Topic> tps = lvls.isEmpty() ? List.of()
                : topics.findByLevelIdInAndStatusOrderBySortOrderAscIdAsc(ids(lvls, CourseLevel::getId), PUBLISHED);
        List<Lesson> lss = tps.isEmpty() ? List.of()
                : lessons.findByTopicIdInAndStatusOrderBySortOrderAscIdAsc(ids(tps, Topic::getId), PUBLISHED);

        Map<Long, List<Lesson>> lessonsByTopic = lss.stream().collect(Collectors.groupingBy(Lesson::getTopicId));
        Map<Long, LessonProgress> done = lss.isEmpty() ? Map.of() : progress.findByUserIdAndLessonIdIn(userId,
                ids(lss, Lesson::getId)).stream().collect(Collectors.toMap(LessonProgress::getLessonId, Function.identity()));
        BankIndex bankIndex = indexBanks(tps, lss);
        Map<Long, Long> xp = xpByTopic(userId, lss, bankIndex.topicOfBank());
        Map<String, Boolean> lockMemo = new HashMap<>();

        List<LevelNode> tree = new ArrayList<>();
        for (CourseLevel lv : lvls) {
            List<TopicNode> topicNodes = new ArrayList<>();
            for (Topic t : tps) {
                if (!lv.getId().equals(t.getLevelId())) {
                    continue;
                }
                boolean locked = lockMemo.computeIfAbsent(lv.getId() + ":" + t.isFreeTrial(),
                        k -> !allowed(userId, t.isFreeTrial(), lv.getId()));
                List<Lesson> topicLessons = lessonsByTopic.getOrDefault(t.getId(), List.of());
                List<LessonNode> nodes = lessonNodes(topicLessons, done);
                int completed = (int) nodes.stream().filter(LessonNode::completed).count();
                topicNodes.add(new TopicNode(t.getId(), t.getTitle(), t.isFreeTrial(), t.getSkill(), locked, completed, nodes.size(),
                        xp.getOrDefault(t.getId(), 0L), status(locked, completed, nodes), nodes,
                        bankIndex.visibleByTopic().getOrDefault(t.getId(), List.of())));
            }
            tree.add(new LevelNode(lv.getId(), lv.getCode(), lv.getTitle(), topicNodes));
        }
        return new Roadmap(tree, resume(tree));
    }

    private boolean allowed(long userId, boolean freeTrial, Long levelId) {
        var probe = new PlayableLesson(null, null, null, null, null, null, freeTrial, levelId, null, null, null, null, null);
        return policies.stream().anyMatch(p -> p.allows(userId, probe));
    }

    private static List<LessonNode> lessonNodes(List<Lesson> topicLessons, Map<Long, LessonProgress> done) {
        List<LessonNode> nodes = new ArrayList<>();
        int order = 1;
        for (Lesson l : topicLessons) {
            LessonProgress p = done.get(l.getId());
            nodes.add(new LessonNode(l.getId(), l.getTitle(), l.getType(), l.getDurationSec(), order++,
                    p != null && p.getCompletedAt() != null, p == null ? 0 : p.getWatchedSec(),
                    p == null ? 0 : p.getMaxPositionSec(), p == null ? null : p.getUpdatedAt()));
        }
        return nodes;
    }

    /** D-59: trong topic mở, bài chưa xong có tiến độ mới nhất; không có thì bài chưa xong đầu tiên theo lộ trình. */
    private static Resume resume(List<LevelNode> tree) {
        Resume first = null;
        Resume recent = null;
        Instant recentAt = null;
        for (LevelNode lv : tree) {
            for (TopicNode t : lv.topics()) {
                if (t.locked()) {
                    continue;
                }
                for (LessonNode n : t.lessons()) {
                    if (n.completed()) {
                        continue;
                    }
                    Resume r = new Resume(n.id(), n.title(), n.order(), t.id(), t.title(), lv.id(), lv.code(), lv.title(),
                            t.completedLessons(), t.totalLessons(), n.maxPositionSec(), n.durationSec());
                    first = first == null ? r : first;
                    if (n.lastWatchedAt() != null && (recentAt == null || n.lastWatchedAt().isAfter(recentAt))) {
                        recent = r;
                        recentAt = n.lastWatchedAt();
                    }
                }
            }
        }
        return recent != null ? recent : first;
    }

    private static String status(boolean locked, int completed, List<LessonNode> nodes) {
        if (locked) {
            return "LOCKED";
        }
        if (!nodes.isEmpty() && completed == nodes.size()) {
            return "DONE";
        }
        boolean started = completed > 0 || nodes.stream().anyMatch(n -> n.watchedSec() > 0);
        return started ? "IN_PROGRESS" : "NOT_STARTED";
    }

    /** visibleByTopic: bộ luyện tập hiển thị; topicOfBank: mọi bộ của khóa (kể cả EXAM) để cộng XP đúng topic. */
    private record BankIndex(Map<Long, List<BankNode>> visibleByTopic, Map<Long, Long> topicOfBank) {}

    private BankIndex indexBanks(List<Topic> tps, List<Lesson> lss) {
        if (tps.isEmpty()) {
            return new BankIndex(Map.of(), Map.of());
        }
        List<QuestionBank> all = banks.findByTopicIdInAndStatusAndPurposeOrderByIdAsc(ids(tps, Topic::getId), PUBLISHED,
                QuestionBank.COURSE);
        Set<Long> lessonIds = lss.stream().map(Lesson::getId).collect(Collectors.toSet());
        // bộ gắn với bài chưa PUBLISHED thì ẩn (cùng quy tắc BankInfo)
        List<QuestionBank> visible = all.stream().filter(b -> QuestionBank.PRACTICE.equals(b.getMode())
                && (b.getLessonId() == null || lessonIds.contains(b.getLessonId()))).toList();
        Map<Long, Long> counts = new HashMap<>();
        if (!visible.isEmpty()) {
            for (Object[] row : questions.countByBankIds(ids(visible, QuestionBank::getId))) {
                counts.put((Long) row[0], (Long) row[1]);
            }
        }
        Map<Long, List<BankNode>> byTopic = new LinkedHashMap<>();
        for (QuestionBank b : visible) {
            byTopic.computeIfAbsent(b.getTopicId(), k -> new ArrayList<>())
                    .add(new BankNode(b.getId(), b.getTitle(), b.getLessonId(), counts.getOrDefault(b.getId(), 0L)));
        }
        return new BankIndex(byTopic, all.stream().collect(Collectors.toMap(QuestionBank::getId, QuestionBank::getTopicId)));
    }

    /** D-52: XP thực nhận, LESSON theo lessonId, EXERCISE và EXERCISE_HIGH theo bankId. */
    private Map<Long, Long> xpByTopic(long userId, List<Lesson> lss, Map<Long, Long> topicOfBank) {
        Map<Long, Long> topicOfLesson = lss.stream().collect(Collectors.toMap(Lesson::getId, Lesson::getTopicId));
        Map<Long, Long> total = new HashMap<>();
        if (!topicOfLesson.isEmpty()) {
            sum(total, xpEvents.findByUserIdAndSourceInAndRefIdIn(userId, List.of("LESSON"), topicOfLesson.keySet()),
                    topicOfLesson);
        }
        if (!topicOfBank.isEmpty()) {
            sum(total, xpEvents.findByUserIdAndSourceInAndRefIdIn(userId, BANK_XP_SOURCES, topicOfBank.keySet()), topicOfBank);
        }
        return total;
    }

    private static void sum(Map<Long, Long> total, Collection<XpEvent> events, Map<Long, Long> topicOfRef) {
        events.forEach(e -> total.merge(topicOfRef.get(e.getRefId()), (long) e.getXp(), Long::sum));
    }

    private static <T> List<Long> ids(Collection<T> items, Function<T, Long> id) {
        return items.stream().map(id).toList();
    }
}
