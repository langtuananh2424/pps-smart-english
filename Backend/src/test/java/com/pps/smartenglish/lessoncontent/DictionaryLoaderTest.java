package com.pps.smartenglish.lessoncontent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.pps.smartenglish.support.ApiTestBase;
import java.io.InputStream;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** D-175: nạp JSONL wiktextract của cả hai edition; chạy lại thì thay toàn bộ. */
class DictionaryLoaderTest extends ApiTestBase {

    @Autowired DictionaryLoader loader;

    private InputStream sample(String name) {
        return getClass().getResourceAsStream("/dictionary/" + name);
    }

    @Test
    void viEditionTakesUpToThreeGlossesAndFirstExample() {
        var r = loader.load(sample("vi.jsonl"), "vi");
        assertEquals(7, r.read());
        assertEquals(2, r.written());
        Map<String, Object> row = jdbc.queryForMap("select * from dictionary_entries where word_key = 'environment'");
        assertEquals("môi trường; hoàn cảnh; bối cảnh", row.get("MEANING_VI"));
        assertEquals("Protect the environment.", row.get("EXAMPLE"));
        assertEquals("noun", row.get("PART_OF_SPEECH"));
        assertNull(jdbc.queryForMap("select * from dictionary_entries where word = 'run'").get("EXAMPLE"));
    }

    @Test
    void enEditionTakesDistinctVietnameseTranslationsAndReloadReplacesAll() {
        loader.load(sample("vi.jsonl"), "vi");
        var r = loader.load(sample("en.jsonl"), "en");
        assertEquals(3, r.read());
        assertEquals(1, r.written());
        assertEquals(1, count("select count(*) from dictionary_entries"));
        Map<String, Object> row = jdbc.queryForMap("select * from dictionary_entries");
        assertEquals("môi trường, hoàn cảnh", row.get("MEANING_VI"));
        assertNull(row.get("EXAMPLE"));
    }

    @Test
    void unknownEditionIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> loader.load(sample("en.jsonl"), "fr"));
    }
}
