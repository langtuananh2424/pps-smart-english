package com.pps.smartenglish.lessoncontent;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** app.lesson.* (D-175): giới hạn phụ đề và glossary, chỉnh bằng cấu hình. */
@ConfigurationProperties(prefix = "app.lesson")
public record LessonContentProperties(@DefaultValue Subtitle subtitle, @DefaultValue Glossary glossary) {

    public record Subtitle(@DefaultValue("1048576") long maxBytes, @DefaultValue("5000") int maxCues) {}

    public record Glossary(@DefaultValue("200") int maxItems) {}
}
