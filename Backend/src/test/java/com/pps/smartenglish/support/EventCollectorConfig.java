package com.pps.smartenglish.support;

import com.pps.smartenglish.shared.event.ExerciseScored;
import com.pps.smartenglish.shared.event.LessonCompleted;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.event.EventListener;

/** Ghi lại LessonCompleted để test kiểm số lần sự kiện được phát. */
@TestConfiguration
public class EventCollectorConfig {

    public static class EventCollector {

        private final List<LessonCompleted> lessons = new CopyOnWriteArrayList<>();

        private final List<ExerciseScored> scored = new CopyOnWriteArrayList<>();

        @EventListener
        public void on(ExerciseScored e) {
            scored.add(e);
        }

        public long exerciseScoredFor(long attemptId) {
            return scored.stream().filter(e -> e.attemptId() == attemptId).count();
        }

        @EventListener
        public void on(LessonCompleted e) {
            lessons.add(e);
        }

        public long lessonCompletedFor(long userId, long lessonId) {
            return lessons.stream().filter(e -> e.userId() == userId && e.lessonId() == lessonId).count();
        }
    }

    @Bean
    EventCollector eventCollector() {
        return new EventCollector();
    }
}
