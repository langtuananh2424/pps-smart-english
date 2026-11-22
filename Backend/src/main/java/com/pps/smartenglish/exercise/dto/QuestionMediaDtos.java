package com.pps.smartenglish.exercise.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

/** Ảnh gắn vào câu hỏi (D-134): cỡ hiển thị là phần trăm khung, mỗi ảnh mỗi câu một cỡ. */
public final class QuestionMediaDtos {

    private QuestionMediaDtos() {}

    public record MediaInput(@NotNull Long assetId,
                             @NotNull @Pattern(regexp = "INLINE|DIAGRAM") String role,
                             @NotNull @Pattern(regexp = "SM|MD|LG|FULL") String displaySize,
                             @NotBlank @Size(max = 300) String altText) {}

    /** Thay toàn bộ danh sách ảnh của câu; thứ tự trong mảng là thứ tự hiển thị. */
    public record ReplaceMedia(@NotNull List<@Valid @NotNull MediaInput> items) {}

    public record QuestionMediaView(Long assetId, String url, int width, int height, String role, String displaySize,
                                    String altText) {}
}
