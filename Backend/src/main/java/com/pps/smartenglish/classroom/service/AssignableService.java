package com.pps.smartenglish.classroom.service;

import com.pps.smartenglish.classroom.dto.AssignmentDtos.AssignableBank;
import com.pps.smartenglish.classroom.dto.AssignmentDtos.AssignableQuestion;
import com.pps.smartenglish.classroom.dto.AssignmentDtos.AssignableTopic;
import com.pps.smartenglish.classroom.dto.AssignmentDtos.AssignableTree;
import com.pps.smartenglish.classroom.repository.AssignableRepository;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** FR-ASG-01: cây nội dung giao được của khóa của lớp, 3 truy vấn cố định. Chỉ nhân sự của lớp (NFR-SEC-05). */
@Service
public class AssignableService {

    static final int TITLE_MAX = 120;

    private final ClassAccess access;
    private final AssignableRepository repo;

    AssignableService(ClassAccess access, AssignableRepository repo) {
        this.access = access;
        this.repo = repo;
    }

    @Transactional(readOnly = true)
    public AssignableTree tree(long callerId, long classId) {
        long courseId = access.requireStaff(callerId, classId).getCourseId();
        Map<Long, List<AssignableBank>> banks = new LinkedHashMap<>();
        for (Object[] r : repo.banks(courseId)) {
            banks.computeIfAbsent(((Number) r[1]).longValue(), k -> new ArrayList<>())
                    .add(new AssignableBank(((Number) r[0]).longValue(), (String) r[2], (String) r[3], ((Number) r[4]).intValue()));
        }
        Map<Long, List<AssignableQuestion>> ai = new LinkedHashMap<>();
        for (Object[] r : repo.aiQuestions(courseId)) {
            String p = (String) r[3];
            ai.computeIfAbsent(((Number) r[1]).longValue(), k -> new ArrayList<>())
                    .add(new AssignableQuestion(((Number) r[0]).longValue(), (String) r[2], p.length() > TITLE_MAX ? p.substring(0, TITLE_MAX) : p));
        }
        List<AssignableTopic> topics = new ArrayList<>();
        for (Object[] r : repo.topics(courseId)) {
            long id = ((Number) r[0]).longValue();
            topics.add(new AssignableTopic(id, (String) r[1], topics.size(), banks.getOrDefault(id, List.of()), ai.getOrDefault(id, List.of())));
        }
        return new AssignableTree(courseId, topics);
    }
}
