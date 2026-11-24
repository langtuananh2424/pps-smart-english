package com.pps.smartenglish.exercise.bulk;

import com.fasterxml.jackson.databind.JsonNode;
import com.pps.smartenglish.exercise.bulk.DuplicateFinder.Candidate;
import com.pps.smartenglish.exercise.bulk.ImportSource.SourceRow;
import com.pps.smartenglish.exercise.bulk.RowChecker.Checked;
import com.pps.smartenglish.exercise.content.PromptHash;
import com.pps.smartenglish.exercise.domain.GrammarElement;
import com.pps.smartenglish.exercise.domain.ImportBatch;
import com.pps.smartenglish.exercise.domain.QuestionBank;
import com.pps.smartenglish.exercise.dto.CmsQuestionDtos.CreateQuestion;
import com.pps.smartenglish.exercise.dto.ImportDtos.BatchView;
import com.pps.smartenglish.exercise.dto.ImportDtos.ConfirmImport;
import com.pps.smartenglish.exercise.dto.ImportDtos.ConfirmRow;
import com.pps.smartenglish.exercise.dto.ImportDtos.Duplicate;
import com.pps.smartenglish.exercise.dto.ImportDtos.ImportResult;
import com.pps.smartenglish.exercise.dto.ImportDtos.PreviewResult;
import com.pps.smartenglish.exercise.dto.ImportDtos.PreviewRow;
import com.pps.smartenglish.exercise.dto.ImportDtos.RowError;
import com.pps.smartenglish.exercise.dto.ImportDtos.SkippedRow;
import com.pps.smartenglish.exercise.repository.ImportBatchRepository;
import com.pps.smartenglish.exercise.repository.QuestionBankRepository;
import com.pps.smartenglish.exercise.repository.QuestionRepository;
import com.pps.smartenglish.exercise.service.CmsQuestionService;
import com.pps.smartenglish.exercise.service.CmsQuestionService.Prepared;
import com.pps.smartenglish.shared.audit.AuditRecorder;
import com.pps.smartenglish.shared.error.ApiException;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Nhập hàng loạt câu hỏi (FR-ADM-02, D-133): xem trước (không ghi câu nào) rồi xác nhận một lô. Xác nhận nhận lại danh sách
 * câu đã sửa từ giao diện thay vì đọc lại file, và kiểm lại từng câu bằng cùng bộ luật như xem trước.
 */
@Service
public class ImportService {

    private static final int SUMMARY_MAX = 120;
    private static final int ERROR_DETAILS_MAX = 20;

    private final QuestionBankRepository banks;
    private final QuestionRepository questions;
    private final ImportBatchRepository batches;
    private final CmsQuestionService cms;
    private final RowChecker checker;
    private final DuplicateFinder duplicates;
    private final TemplateRegistry templates;
    private final ImportProperties props;
    private final AuditRecorder audit;
    private final Clock clock;

    ImportService(QuestionBankRepository banks, QuestionRepository questions, ImportBatchRepository batches, CmsQuestionService cms,
                  RowChecker checker, DuplicateFinder duplicates, TemplateRegistry templates, ImportProperties props,
                  AuditRecorder audit, Clock clock) {
        this.banks = banks;
        this.questions = questions;
        this.batches = batches;
        this.cms = cms;
        this.checker = checker;
        this.duplicates = duplicates;
        this.templates = templates;
        this.props = props;
        this.audit = audit;
        this.clock = clock;
    }

    public ImportSource pasteSource(String text) {
        return new PasteSource(text, templates, props);
    }

    public ImportSource excelSource(byte[] data) {
        return new ExcelSource(data, templates, props);
    }

    /** Không tạo câu nào; chỉ có thể điền dấu vân tay cho câu cũ khi so trùng (xem DuplicateFinder). */
    @Transactional
    public PreviewResult preview(long bankId, ImportSource source) {
        QuestionBank bank = importableBank(bankId);
        var parsed = source.read();
        if (parsed.rows().isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "import_empty", "Không có câu hỏi nào để nhập");
        }
        Map<String, GrammarElement> elements = cms.grammarElements();
        List<PreviewRow> rows = new ArrayList<>();
        List<Candidate> candidates = new ArrayList<>();
        for (SourceRow sr : parsed.rows()) {
            rows.add(previewRow(bank, sr, elements, candidates, rows.size()));
        }
        var dups = duplicates.find(bank, candidates);
        List<PreviewRow> withDups = new ArrayList<>();
        for (int i = 0; i < rows.size(); i++) {
            var r = rows.get(i);
            withDups.add(new PreviewRow(r.sheet(), r.line(), r.type(), r.prompt(), r.valid(), r.errors(),
                    dups.getOrDefault(i, List.<Duplicate>of()), r.question()));
        }
        int valid = (int) withDups.stream().filter(PreviewRow::valid).count();
        return new PreviewResult(source.name(), withDups.size(), valid, withDups.size() - valid, withDups, parsed.warnings());
    }

    private PreviewRow previewRow(QuestionBank bank, SourceRow sr, Map<String, GrammarElement> elements, List<Candidate> candidates,
                                  int index) {
        if (sr.error() != null) {
            return invalid(sr, null, List.of(new RowError("row", sr.error())));
        }
        CreateQuestion req;
        try {
            var cells = new RowCells();
            sr.cells().forEach(cells::put);
            req = sr.template().toRequest(cells);
        } catch (RowException e) {
            return invalid(sr, null, List.of(new RowError(e.field(), e.getMessage())));
        }
        Checked c = checker.check(bank, req, elements);
        if (c.prepared() == null) {
            return invalid(sr, req, c.errors());
        }
        candidates.add(new Candidate(index, sr.line(), PromptHash.of(c.prepared().prompt(), c.prepared().draft().payload())));
        return new PreviewRow(sr.sheet(), sr.line(), req.type(), summary(c.prepared()), true, List.of(), List.of(), req);
    }

    private static PreviewRow invalid(SourceRow sr, CreateQuestion req, List<RowError> errors) {
        return new PreviewRow(sr.sheet(), sr.line(), req == null ? null : req.type(), req == null ? null : abbreviate(promptOf(req)),
                false, errors, List.of(), req);
    }

    /** Một lô: tất cả câu đã kiểm cùng được ghi hoặc không câu nào (một transaction). */
    @Transactional
    public ImportResult confirm(long bankId, long userId, ConfirmImport req) {
        if (banks.lockById(bankId).isEmpty()) { // tuần tự hóa các lần nhập vào cùng một bộ để thứ tự câu không đè nhau
            throw new ApiException(HttpStatus.NOT_FOUND, "bank_not_found", "Không tìm thấy bộ câu hỏi");
        }
        QuestionBank bank = importableBank(bankId);
        if (req.rows().size() > props.maxRows()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "import_too_many_rows", "Tối đa " + props.maxRows() + " câu mỗi lần nhập");
        }
        Map<String, GrammarElement> elements = cms.grammarElements();
        List<Prepared> ok = new ArrayList<>();
        List<SkippedRow> skipped = new ArrayList<>();
        for (ConfirmRow row : req.rows()) {
            Checked c = checker.check(bank, row.question(), elements);
            if (c.prepared() != null) {
                ok.add(c.prepared());
            } else {
                skipped.add(new SkippedRow(row.line(), c.errors()));
            }
        }
        if (!skipped.isEmpty() && !"SKIP_INVALID".equals(req.mode())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "import_has_errors",
                    "Có " + skipped.size() + " dòng lỗi, chưa nhập câu nào", errorDetails(skipped), null);
        }
        if (ok.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "import_empty", "Không có câu hợp lệ nào để nhập");
        }
        var batch = batches.save(new ImportBatch(bankId, userId, req.source(), ok.size(), Instant.now(clock)));
        int order = questions.maxSortOrder(bankId);
        for (Prepared p : ok) {
            cms.insert(bank, p, ++order, batch.getId());
        }
        audit.record("IMPORT", "IMPORT_BATCH", batch.getId(), null, view(batch));
        return new ImportResult(batch.getId(), ok.size(), skipped);
    }

    @Transactional(readOnly = true)
    public List<BatchView> history(long bankId) {
        if (!banks.existsById(bankId)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "bank_not_found", "Không tìm thấy bộ câu hỏi");
        }
        return batches.findTop50ByBankIdOrderByIdDesc(bankId).stream().map(ImportService::view).toList();
    }

    public static BatchView view(ImportBatch b) {
        return new BatchView(b.getId(), b.getBankId(), b.getUserId(), b.getSource(), b.getStatus(), b.getQuestionCount(),
                b.getCreatedAt(), b.getUndoneAt());
    }

    /** Đề kiểm tra đầu vào bắt buộc skill và chỉ nhận dạng chấm tự động nên chưa nằm trong nhập hàng loạt. */
    private QuestionBank importableBank(long bankId) {
        QuestionBank bank = banks.findById(bankId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "bank_not_found", "Không tìm thấy bộ câu hỏi"));
        if (bank.isPlacement()) {
            throw ApiException.validation("bank", "Đề kiểm tra đầu vào chưa hỗ trợ nhập hàng loạt");
        }
        return bank;
    }

    private static Map<String, String> errorDetails(List<SkippedRow> skipped) {
        Map<String, String> d = new LinkedHashMap<>();
        for (SkippedRow s : skipped.stream().limit(ERROR_DETAILS_MAX).toList()) {
            var e = s.errors().get(0);
            d.put("dòng " + s.line(), e.field() + ": " + e.message());
        }
        return d;
    }

    /** Chữ đại diện cho giao diện: nội dung thật của dạng (đoạn điền, câu cần sửa...) hơn là lời hướng dẫn chung. */
    private static String summary(Prepared p) {
        JsonNode payload = p.draft().payload();
        if (payload != null) {
            for (String f : List.of("text", "sentence", "task")) {
                if (payload.path(f).isTextual()) {
                    return abbreviate(payload.get(f).asText());
                }
            }
            if (payload.path("tokens").isArray()) {
                List<String> t = new ArrayList<>();
                payload.get("tokens").forEach(n -> t.add(n.asText()));
                return abbreviate(String.join(" ", t));
            }
        }
        return abbreviate(p.prompt());
    }

    private static String promptOf(CreateQuestion r) {
        return r.prompt() != null ? r.prompt() : String.valueOf(r.promptHtml());
    }

    private static String abbreviate(String s) {
        return s == null || s.length() <= SUMMARY_MAX ? s : s.substring(0, SUMMARY_MAX) + "…";
    }
}
