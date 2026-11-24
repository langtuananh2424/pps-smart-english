package com.pps.smartenglish.exercise.dto;

import com.pps.smartenglish.exercise.dto.CmsQuestionDtos.CreateQuestion;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;

/** DTO nhập hàng loạt câu hỏi (FR-ADM-02, D-133, D-138). */
public final class ImportDtos {

    private ImportDtos() {}

    public record RowError(String field, String message) {}

    /** scope: FILE (trùng dòng trước trong cùng lần nhập), BANK (cùng bộ), TOPIC (bộ khác cùng topic). */
    public record Duplicate(String scope, Long questionId, Long bankId, String bankTitle, Integer line) {}

    /** question có khi đọc được dòng (kể cả khi sai nội dung) để giao diện sửa tại chỗ rồi gửi lại ở bước xác nhận. */
    public record PreviewRow(String sheet, int line, String type, String prompt, boolean valid, List<RowError> errors,
                             List<Duplicate> duplicates, CreateQuestion question) {}

    public record PreviewResult(String source, int total, int valid, int invalid, List<PreviewRow> rows, List<String> warnings) {}

    public record ConfirmRow(int line, @NotNull CreateQuestion question) {}

    /** mode: ALL_OR_NOTHING (mặc định, có dòng sai thì không ghi gì) hoặc SKIP_INVALID. */
    public record ConfirmImport(@NotNull @Pattern(regexp = "PASTE|EXCEL", message = "Chỉ nhận PASTE hoặc EXCEL") String source,
                                @Pattern(regexp = "ALL_OR_NOTHING|SKIP_INVALID", message = "Chỉ nhận ALL_OR_NOTHING hoặc SKIP_INVALID")
                                String mode,
                                @NotNull @Size(min = 1, max = 5000) List<ConfirmRow> rows) {}

    public record SkippedRow(int line, List<RowError> errors) {}

    public record ImportResult(Long batchId, int imported, List<SkippedRow> skipped) {}

    public record BatchView(Long id, Long bankId, Long userId, String source, String status, int questionCount, Instant createdAt,
                            Instant undoneAt) {}

    /** deleted: câu chưa có lượt làm bị xóa; archived: câu đã có lượt làm bị ẩn, lịch sử học viên còn nguyên. */
    public record UndoResult(Long batchId, int deleted, int archived) {}

    public record TemplateView(String type, String label, List<String> columns, List<String> notes, List<List<String>> samples,
                               boolean pasteSupported) {}
}
