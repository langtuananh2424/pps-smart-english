package com.pps.smartenglish.lessoncontent;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.GZIPInputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Component;

/** Lệnh một lần: có app.dictionary.import-file thì nhập xong rồi thoát (xem README). */
@Component
@ConditionalOnProperty("app.dictionary.import-file")
class DictionaryImportRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DictionaryImportRunner.class);

    private final DictionaryLoader loader;
    private final ConfigurableApplicationContext ctx;
    private final String file;
    private final String edition;

    DictionaryImportRunner(DictionaryLoader loader, ConfigurableApplicationContext ctx,
                           @Value("${app.dictionary.import-file}") String file,
                           @Value("${app.dictionary.import-edition:vi}") String edition) {
        this.loader = loader;
        this.ctx = ctx;
        this.file = file;
        this.edition = edition;
    }

    @Override
    public void run(ApplicationArguments args) {
        int code = 0;
        try (InputStream raw = Files.newInputStream(Path.of(file));
             InputStream in = file.endsWith(".gz") ? new GZIPInputStream(raw) : raw) {
            loader.load(in, edition);
        } catch (Exception e) {
            log.error("Nhập từ điển lỗi", e);
            code = 1;
        }
        int exit = code;
        System.exit(SpringApplication.exit(ctx, () -> exit));
    }
}
