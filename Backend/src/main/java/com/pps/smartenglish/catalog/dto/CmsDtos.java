package com.pps.smartenglish.catalog.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * DTO của CMS (FR-ADM-01). PATCH: trường null = vắng mặt = giữ nguyên (nên không xóa trắng được description về null;
 * gửi chuỗi rỗng để xóa nội dung). Không có trường cha: level/topic/lesson không đổi cha.
 */
public final class CmsDtos {

    private static final String NOT_BLANK = ".*\\S.*";
    private static final String SLUG = "[a-z0-9]+(-[a-z0-9]+)*";

    private CmsDtos() {}

    // ---- yêu cầu ----

    public record CreateCourse(
            @NotBlank @Size(max = 120) @Pattern(regexp = SLUG, message = "Chỉ gồm chữ thường, số và dấu gạch ngang") String slug,
            @NotBlank @Size(max = 200) String title,
            @Size(max = 4000) String description,
            @Size(max = 30) String targetType) {}

    public record PatchCourse(
            @Size(max = 120) @Pattern(regexp = SLUG, message = "Chỉ gồm chữ thường, số và dấu gạch ngang") String slug,
            @Size(max = 200) @Pattern(regexp = NOT_BLANK, message = "Không được để trống") String title,
            @Size(max = 4000) String description,
            @Size(max = 30) String targetType) {}

    public record CreateLevel(
            @NotBlank @Size(max = 20) String code,
            @NotBlank @Size(max = 200) String title,
            @Min(0) Integer sortOrder) {}

    public record PatchLevel(
            @Size(max = 20) @Pattern(regexp = NOT_BLANK, message = "Không được để trống") String code,
            @Size(max = 200) @Pattern(regexp = NOT_BLANK, message = "Không được để trống") String title,
            @Min(0) Integer sortOrder) {}

    public record CreateTopic(@NotBlank @Size(max = 200) String title, @Min(0) Integer sortOrder, Boolean isFreeTrial) {}

    public record PatchTopic(
            @Size(max = 200) @Pattern(regexp = NOT_BLANK, message = "Không được để trống") String title,
            @Min(0) Integer sortOrder,
            Boolean isFreeTrial) {}

    /** type: CONNECTIVE_VIDEO hoặc REFLEX_VIDEO. videoSource chỉ nhận YOUTUBE; videoRef là mã hoặc URL YouTube. */
    public record CreateLesson(
            @NotBlank @Pattern(regexp = "CONNECTIVE_VIDEO|REFLEX_VIDEO", message = "CONNECTIVE_VIDEO hoặc REFLEX_VIDEO") String type,
            @NotBlank @Size(max = 200) String title,
            @Min(1) Integer durationSec,
            @Min(0) Integer sortOrder,
            String videoSource,
            @Size(max = 500) String videoRef) {}

    public record PatchLesson(
            @Pattern(regexp = "CONNECTIVE_VIDEO|REFLEX_VIDEO", message = "CONNECTIVE_VIDEO hoặc REFLEX_VIDEO") String type,
            @Size(max = 200) @Pattern(regexp = NOT_BLANK, message = "Không được để trống") String title,
            @Min(1) Integer durationSec,
            @Min(0) Integer sortOrder,
            String videoSource,
            @Size(max = 500) String videoRef) {}

    // ---- phản hồi ----

    public record CourseView(Long id, String slug, String title, String description, String targetType, String status) {}

    public record LevelView(Long id, Long courseId, String code, String title, int sortOrder, String status) {}

    public record TopicView(Long id, Long levelId, String title, int sortOrder, boolean isFreeTrial, String status) {}

    public record LessonView(Long id, Long topicId, String type, String title, Integer durationSec, int sortOrder, String status,
                             String videoSource, String videoRef) {}

    /** Cây đầy đủ kể cả DRAFT cho màn soạn. */
    public record CourseTree(CourseView course, List<LevelNode> levels) {}

    public record LevelNode(LevelView level, List<TopicNode> topics) {}

    public record TopicNode(TopicView topic, List<LessonView> lessons) {}
}
