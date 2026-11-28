package com.pps.smartenglish.lessoncontent;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pps.smartenglish.lessoncontent.WiktionaryEntryMapper.Row;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/** D-175: nạp từ điển từ JSONL wiktextract. Chạy lại thì xóa hết rồi nạp lại trong một transaction (bảng chỉ có dữ liệu nhập). */
@Service
public class DictionaryLoader {

    public record Result(long read, long written) {}

    private static final Logger log = LoggerFactory.getLogger(DictionaryLoader.class);
    private static final int BATCH = 1000;
    private static final Set<String> EDITIONS = Set.of("vi", "en");
    private static final String INSERT = "insert into dictionary_entries (word, word_key, part_of_speech, meaning_vi, example) values (?, ?, ?, ?, ?)";

    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final ObjectMapper mapper;

    DictionaryLoader(JdbcTemplate jdbc, TransactionTemplate tx, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.tx = tx;
        this.mapper = mapper;
    }

    /** Đọc từng dòng, không nạp cả file vào RAM. Dòng JSON hỏng bị bỏ qua và đếm vào log. */
    public Result load(InputStream in, String edition) {
        if (!EDITIONS.contains(edition)) {
            throw new IllegalArgumentException("edition phải là vi hoặc en: " + edition);
        }
        return tx.execute(status -> {
            jdbc.update("delete from dictionary_entries");
            long read = 0;
            long written = 0;
            long bad = 0;
            List<Row> batch = new ArrayList<>(BATCH);
            try (var r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                for (String line = r.readLine(); line != null; line = r.readLine()) {
                    if (line.isBlank()) {
                        continue;
                    }
                    read++;
                    try {
                        WiktionaryEntryMapper.map(mapper.readTree(line), edition).ifPresent(batch::add);
                    } catch (JsonProcessingException e) {
                        bad++;
                    }
                    if (batch.size() >= BATCH) {
                        written += flush(batch);
                    }
                }
            } catch (IOException e) {
                throw new java.io.UncheckedIOException(e);
            }
            written += flush(batch);
            log.info("Nhập từ điển {}: đọc {} dòng, ghi {} mục, bỏ {} dòng JSON hỏng", edition, read, written, bad);
            return new Result(read, written);
        });
    }

    private int flush(List<Row> batch) {
        int n = batch.size();
        if (n > 0) {
            jdbc.batchUpdate(INSERT, batch, BATCH, (ps, row) -> {
                ps.setString(1, row.word());
                ps.setString(2, row.word().toLowerCase(Locale.ROOT));
                ps.setString(3, row.pos());
                ps.setString(4, row.meaningVi());
                ps.setString(5, row.example());
            });
            batch.clear();
        }
        return n;
    }
}
