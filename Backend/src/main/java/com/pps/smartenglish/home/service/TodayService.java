package com.pps.smartenglish.home.service;

import com.pps.smartenglish.ai.repository.WritingSessionRepository;
import com.pps.smartenglish.exercise.repository.QuestionRepository;
import com.pps.smartenglish.exercise.repository.StudentAttemptRepository;
import com.pps.smartenglish.home.dto.HomeDtos.BankNode;
import com.pps.smartenglish.home.dto.HomeDtos.LessonNode;
import com.pps.smartenglish.home.dto.HomeDtos.MyCourse;
import com.pps.smartenglish.home.dto.HomeDtos.Roadmap;
import com.pps.smartenglish.home.dto.HomeDtos.TaskKind;
import com.pps.smartenglish.home.dto.HomeDtos.Today;
import com.pps.smartenglish.home.dto.HomeDtos.TodayTask;
import com.pps.smartenglish.home.dto.HomeDtos.TopicNode;
import com.pps.smartenglish.lms.domain.LessonProgress;
import com.pps.smartenglish.lms.repository.LessonProgressRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * GET /api/me/today (D-51): tối đa 3 việc suy ra từ lộ trình, không có bảng riêng.
 * Việc neo vào một bài (anchor): bài vừa hoàn thành hôm nay (bài xếp sau cùng trong lộ trình nếu có nhiều), nếu không thì bài
 * "học tiếp" của lộ trình. Nhờ vậy việc vừa xong vẫn nằm trong danh sách kèm doneToday=true (đếm x/3) thay vì nhảy sang việc sau.
 */
@Service
public class TodayService {

    private final MyCourseService myCourses;
    private final RoadmapService roadmaps;
    private final VnDay day;
    private final LessonProgressRepository progress;
    private final StudentAttemptRepository attempts;
    private final QuestionRepository questions;
    private final WritingSessionRepository writing;

    TodayService(MyCourseService myCourses, RoadmapService roadmaps, VnDay day, LessonProgressRepository progress,
                 StudentAttemptRepository attempts, QuestionRepository questions, WritingSessionRepository writing) {
        this.myCourses = myCourses;
        this.roadmaps = roadmaps;
        this.day = day;
        this.progress = progress;
        this.attempts = attempts;
        this.questions = questions;
        this.writing = writing;
    }

    /** courseId null: khóa đầu của /api/me/courses; không có khóa nào thì danh sách rỗng. */
    @Transactional(readOnly = true)
    public Today today(long userId, Long courseId) {
        Long id = courseId != null ? courseId
                : myCourses.list(userId).stream().findFirst().map(MyCourse::courseId).orElse(null);
        if (id == null) {
            return new Today(null, List.of(), 0, 0);
        }
        Roadmap map = roadmaps.of(userId, id);
        List<Slot> slots = unlockedSlots(map);
        Set<Long> doneToday = completedToday(userId, slots);
        Slot anchor = slots.stream().filter(s -> doneToday.contains(s.lesson().id())).reduce((a, b) -> b)
                .orElseGet(() -> resumeSlot(slots));
        List<TodayTask> tasks = new ArrayList<>();
        if (anchor != null) {
            tasks.add(video(anchor, doneToday.contains(anchor.lesson().id())));
            exercise(userId, anchor).ifPresent(tasks::add);
            writingTask(userId, anchor).ifPresent(tasks::add);
        }
        int done = (int) tasks.stream().filter(TodayTask::doneToday).count();
        return new Today(id, tasks, done, tasks.size());
    }

    /** Bài của topic mở cho người dùng, đúng thứ tự lộ trình. */
    private record Slot(TopicNode topic, LessonNode lesson) {}

    private static List<Slot> unlockedSlots(Roadmap map) {
        List<Slot> out = new ArrayList<>();
        map.levels().forEach(lv -> lv.topics().stream().filter(t -> !t.locked())
                .forEach(t -> t.lessons().forEach(l -> out.add(new Slot(t, l)))));
        return out;
    }

    private static Slot resumeSlot(List<Slot> slots) {
        // D-51: việc hôm nay theo thứ tự lộ trình, không theo bài xem gần nhất của resume
        return slots.stream().filter(s -> !s.lesson().completed()).findFirst().orElse(null);
    }

    private Set<Long> completedToday(long userId, List<Slot> slots) {
        if (slots.isEmpty()) {
            return Set.of();
        }
        var ids = slots.stream().map(s -> s.lesson().id()).toList();
        return progress.findByUserIdAndLessonIdInAndCompletedAtGreaterThanEqualAndCompletedAtLessThan(userId, ids,
                day.start(), day.end()).stream().map(LessonProgress::getLessonId).collect(Collectors.toSet());
    }

    private static TodayTask video(Slot s, boolean done) {
        LessonNode l = s.lesson();
        Integer remaining = l.durationSec() == null ? null : Math.max(0, l.durationSec() - l.watchedSec());
        return new TodayTask(TaskKind.VIDEO, l.title(), s.topic().title(), l.id(), null, null, done ? Integer.valueOf(0) : remaining,
                null, done);
    }

    /** Bộ của chính bài trước, rồi bộ khác của topic; bỏ bộ đã nộp từ hôm trước trở về trước, giữ bộ vừa nộp hôm nay. */
    private Optional<TodayTask> exercise(long userId, Slot s) {
        List<BankNode> banks = s.topic().banks();
        if (banks.isEmpty()) {
            return Optional.empty();
        }
        Map<Long, Instant> last = lastSubmitted(attempts.lastSubmitted(userId, banks.stream().map(BankNode::id).toList()));
        return java.util.stream.Stream.concat(banks.stream().filter(b -> s.lesson().id().equals(b.lessonId())),
                        banks.stream().filter(b -> !s.lesson().id().equals(b.lessonId())))
                .filter(b -> !last.containsKey(b.id()) || day.isToday(last.get(b.id()))).findFirst()
                .map(b -> new TodayTask(TaskKind.EXERCISE, b.title(), s.topic().title(), s.lesson().id(), b.id(), null, null,
                        b.questionCount(), day.isToday(last.get(b.id()))));
    }

    /** Câu WRITING đầu tiên của topic mà chưa nộp từ hôm trước trở về trước (giữ câu vừa nộp hôm nay). */
    private Optional<TodayTask> writingTask(long userId, Slot s) {
        if (s.topic().banks().isEmpty()) {
            return Optional.empty();
        }
        List<Long> qIds = questions.findWritingIds(s.topic().banks().stream().map(BankNode::id).toList());
        if (qIds.isEmpty()) {
            return Optional.empty();
        }
        Map<Long, Instant> last = lastSubmitted(writing.lastSubmitted(userId, qIds));
        return qIds.stream().filter(q -> !last.containsKey(q) || day.isToday(last.get(q))).findFirst()
                .map(q -> new TodayTask(TaskKind.WRITING, "Bài viết", s.topic().title(), null, null, q, null, null,
                        day.isToday(last.get(q))));
    }

    private static Map<Long, Instant> lastSubmitted(Collection<Object[]> rows) {
        Map<Long, Instant> out = new HashMap<>();
        rows.forEach(r -> out.put((Long) r[0], (Instant) r[1]));
        return out;
    }
}
