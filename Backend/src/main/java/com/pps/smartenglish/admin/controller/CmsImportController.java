package com.pps.smartenglish.admin.controller;

import com.pps.smartenglish.exercise.bulk.ImportGuide;
import com.pps.smartenglish.exercise.bulk.ImportProperties;
import com.pps.smartenglish.exercise.bulk.ImportService;
import com.pps.smartenglish.exercise.bulk.ImportTemplate;
import com.pps.smartenglish.exercise.bulk.ImportUndoService;
import com.pps.smartenglish.exercise.bulk.TemplateRegistry;
import com.pps.smartenglish.exercise.bulk.TemplateWorkbook;
import com.pps.smartenglish.exercise.dto.ImportDtos.BatchView;
import com.pps.smartenglish.exercise.dto.ImportDtos.ConfirmImport;
import com.pps.smartenglish.exercise.dto.ImportDtos.ImportResult;
import com.pps.smartenglish.exercise.dto.ImportDtos.PreviewResult;
import com.pps.smartenglish.exercise.dto.ImportDtos.TemplateView;
import com.pps.smartenglish.exercise.dto.ImportDtos.UndoResult;
import com.pps.smartenglish.shared.error.ApiException;
import com.pps.smartenglish.shared.security.CurrentUser;
import com.pps.smartenglish.shared.web.PathId;
import jakarta.validation.Valid;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * FR-ADM-02, D-133, D-138: nhập hàng loạt câu hỏi (dán nhanh, Excel), hoàn tác theo lô, file mẫu và hướng dẫn nhờ AI.
 * Cùng quyền cms.content như CmsQuestionController (SecurityConfig); CMS dùng chung cho mọi biên tập viên nên hoàn tác không giới hạn theo người nhập.
 */
@RestController
@RequestMapping("/api/admin/cms")
class CmsImportController {

    private static final MediaType XLSX = MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    private final ImportService imports;
    private final ImportUndoService undoer;
    private final TemplateRegistry templates;
    private final TemplateWorkbook workbook;
    private final ImportGuide guide;
    private final ImportProperties props;

    CmsImportController(ImportService imports, ImportUndoService undoer, TemplateRegistry templates, TemplateWorkbook workbook,
                        ImportGuide guide, ImportProperties props) {
        this.imports = imports;
        this.undoer = undoer;
        this.templates = templates;
        this.workbook = workbook;
        this.guide = guide;
        this.props = props;
    }

    /** Gửi đúng một trong hai: text (dán nhanh) hoặc file (.xlsx). Chưa ghi câu nào. */
    @PostMapping("/banks/{id}/imports/preview")
    PreviewResult preview(@PathVariable String id, @RequestParam(required = false) String text,
                          @RequestParam(required = false) MultipartFile file) throws IOException {
        long bankId = bankId(id);
        if ((text == null) == (file == null)) {
            throw ApiException.validation("text", "Gửi đúng một trong hai: text (dán nhanh) hoặc file (.xlsx)");
        }
        if (text != null) {
            return imports.preview(bankId, imports.pasteSource(text));
        }
        if (file.getSize() > props.maxFileBytes()) { // chặn trước khi đọc vào RAM
            throw new ApiException(HttpStatus.BAD_REQUEST, "import_file_too_large", "File quá lớn (tối đa " + props.maxFileBytes() / 1024 + " KB)");
        }
        return imports.preview(bankId, imports.excelSource(file.getBytes()));
    }

    @PostMapping("/banks/{id}/imports")
    @ResponseStatus(HttpStatus.CREATED)
    ImportResult confirm(@PathVariable String id, @Valid @RequestBody ConfirmImport req, Authentication auth) {
        return imports.confirm(bankId(id), CurrentUser.id(auth), req);
    }

    @GetMapping("/banks/{id}/imports")
    List<BatchView> history(@PathVariable String id) {
        return imports.history(bankId(id));
    }

    @PostMapping("/imports/{id}/undo")
    UndoResult undo(@PathVariable String id) {
        return undoer.undo(PathId.parse(id, "import_batch_not_found", "Không tìm thấy lô nhập"));
    }

    @GetMapping("/import/templates")
    List<TemplateView> templates() {
        return templates.all().stream().map(CmsImportController::view).toList();
    }

    /** type để trống = đủ mọi dạng, mỗi dạng một sheet. */
    @GetMapping("/import/templates.xlsx")
    ResponseEntity<byte[]> templateFile(@RequestParam(required = false) String type) {
        List<ImportTemplate> chosen = type == null ? templates.all()
                : List.of(templates.byType(type).orElseThrow(() -> ApiException.validation("type", "Dạng câu hỏi không hợp lệ")));
        return ResponseEntity.ok().contentType(XLSX)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"mau-nhap-" + (type == null ? "tat-ca" : type.toLowerCase()) + ".xlsx\"")
                .body(workbook.build(chosen));
    }

    @GetMapping("/import/guide")
    ResponseEntity<byte[]> guide() {
        return ResponseEntity.ok().contentType(new MediaType("text", "markdown", StandardCharsets.UTF_8))
                .body(guide.markdown().getBytes(StandardCharsets.UTF_8));
    }

    private static TemplateView view(ImportTemplate t) {
        return new TemplateView(t.type().name(), t.label(), t.columns(), t.columnNotes(), t.samples(),
                TemplateRegistry.pasteSupported(t.type()));
    }

    private static long bankId(String raw) {
        return PathId.parse(raw, "bank_not_found", "Không tìm thấy bộ câu hỏi");
    }
}
