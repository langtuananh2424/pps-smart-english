package com.pps.smartenglish.exercise.bulk;

import com.pps.smartenglish.exercise.domain.Question;
import com.pps.smartenglish.exercise.dto.ImportDtos.UndoResult;
import com.pps.smartenglish.exercise.repository.ImportBatchRepository;
import com.pps.smartenglish.exercise.repository.QuestionChoiceRepository;
import com.pps.smartenglish.exercise.repository.QuestionRepository;
import com.pps.smartenglish.exercise.repository.QuestionTagRepository;
import com.pps.smartenglish.shared.audit.AuditRecorder;
import com.pps.smartenglish.shared.error.ApiException;
import java.time.Clock;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Hoàn tác một lô nhập (D-138), luôn được. Câu chưa có người dùng tham chiếu thì xóa; câu đã có lượt làm (hoặc phiên viết,
 * nói, bài giao) thì chỉ ẩn bằng archived_at để lịch sử học viên còn nguyên.
 */
@Service
public class ImportUndoService {

    private final ImportBatchRepository batches;
    private final QuestionRepository questions;
    private final QuestionChoiceRepository choices;
    private final QuestionTagRepository tags;
    private final AuditRecorder audit;
    private final Clock clock;

    ImportUndoService(ImportBatchRepository batches, QuestionRepository questions, QuestionChoiceRepository choices,
                      QuestionTagRepository tags, AuditRecorder audit, Clock clock) {
        this.batches = batches;
        this.questions = questions;
        this.choices = choices;
        this.tags = tags;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional
    public UndoResult undo(long batchId) {
        var batch = batches.lockById(batchId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "import_batch_not_found", "Không tìm thấy lô nhập"));
        if ("UNDONE".equals(batch.getStatus())) {
            throw new ApiException(HttpStatus.CONFLICT, "import_already_undone", "Lô nhập này đã được hoàn tác");
        }
        var before = ImportService.view(batch);
        Instant now = Instant.now(clock);
        Set<Long> used = new HashSet<>(questions.findUsedIdsInBatch(batchId));
        List<Question> rows = questions.findByImportBatchId(batchId);
        List<Question> toArchive = rows.stream().filter(q -> used.contains(q.getId())).toList();
        List<Question> toDelete = rows.stream().filter(q -> !used.contains(q.getId())).toList();

        toArchive.stream().filter(q -> q.getArchivedAt() == null).forEach(q -> q.archive(now));
        questions.saveAll(toArchive);
        if (!toDelete.isEmpty()) {
            List<Long> ids = toDelete.stream().map(Question::getId).toList();
            choices.deleteByQuestionIdIn(ids);
            tags.deleteByQuestionIdIn(ids);
            choices.flush();
            tags.flush();
            questions.deleteAll(toDelete);
            questions.flush();
        }
        batch.undo(now);
        batches.save(batch);
        var result = new UndoResult(batchId, toDelete.size(), toArchive.size());
        audit.record("UNDO_IMPORT", "IMPORT_BATCH", batchId, before, ImportService.view(batch));
        return result;
    }
}
