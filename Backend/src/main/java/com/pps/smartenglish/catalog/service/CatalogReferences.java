package com.pps.smartenglish.catalog.service;

import com.pps.smartenglish.catalog.repository.LessonRepository;
import com.pps.smartenglish.catalog.repository.TopicRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Cổng công khai để module khác (exercise) kiểm tra topic/bài học tồn tại mà không đụng repository của catalog. */
@Service
public class CatalogReferences {

    private final TopicRepository topics;
    private final LessonRepository lessons;

    CatalogReferences(TopicRepository topics, LessonRepository lessons) {
        this.topics = topics;
        this.lessons = lessons;
    }

    @Transactional(readOnly = true)
    public boolean topicExists(long topicId) {
        return topics.existsById(topicId);
    }

    /** Mọi trạng thái (CMS soạn cả bài còn DRAFT). */
    @Transactional(readOnly = true)
    public boolean lessonBelongsToTopic(long lessonId, long topicId) {
        return lessons.findById(lessonId).map(l -> l.getTopicId().equals(topicId)).orElse(false);
    }
}
