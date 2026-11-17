package com.pps.smartenglish.ai.speaking;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pps.smartenglish.ai.speaking.EngineModels.Analysis;
import com.pps.smartenglish.ai.speaking.EngineModels.Audio;
import com.pps.smartenglish.ai.speaking.EngineModels.Transcript;
import com.pps.smartenglish.ai.speaking.EngineModels.Word;
import com.pps.smartenglish.ai.speaking.EngineModels.WordScore;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/**
 * HTTP multipart tới engine. Connect 5 s, read 180 s. Semaphore giới hạn request đồng thời vì engine dùng chung.
 * Không log token và không log nội dung phản hồi.
 */
@Component
@ConditionalOnProperty(prefix = "app.speaking", name = "engine", havingValue = "http", matchIfMissing = true)
class HttpSpeakingEngineClient implements SpeakingEngineClient {

    private final String baseUrl;
    private final String token;
    private final Semaphore slots;
    private final Duration waitForSlot;
    private final ObjectMapper mapper;
    private final RestClient http;

    HttpSpeakingEngineClient(SpeakingProperties props, ObjectMapper mapper) {
        this.baseUrl = props.engineUrl().replaceAll("/+$", "");
        this.token = props.engineToken();
        this.slots = new Semaphore(Math.max(1, props.maxConcurrent()), true);
        this.waitForSlot = props.readTimeout();
        this.mapper = mapper;
        // Engine chạy uvicorn: request kèm "Upgrade: h2c" (mặc định của JDK) bị mất body và trả 422.
        var factory = new JdkClientHttpRequestFactory(HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1).connectTimeout(props.connectTimeout()).build());
        factory.setReadTimeout(props.readTimeout());
        this.http = RestClient.builder().requestFactory(factory).build();
    }

    @Override
    public Transcript transcribe(Audio audio) throws SpeakingEngineException {
        var form = form(audio);
        form.add("language", "en");
        JsonNode r = post("/v1/transcribe", form);
        List<Word> words = new ArrayList<>();
        for (JsonNode w : r.path("words")) {
            words.add(new Word(w.path("word").asText(), w.path("start").asDouble(), w.path("end").asDouble(), w.path("probability").asDouble()));
        }
        return new Transcript(r.path("text").asText(), words);
    }

    @Override
    public Analysis analyze(Audio audio, List<String> words) throws SpeakingEngineException {
        var form = form(audio);
        form.add("words", mapper.valueToTree(words).toString());
        form.add("gopt_model", "");
        form.add("ipa_model", "");
        form.add("return_features", "0");
        JsonNode r = post("/v1/analyze", form);
        List<WordScore> out = new ArrayList<>();
        JsonNode ipa = r.path("ipa").path("words");
        for (int i = 0; i < ipa.size(); i++) {
            JsonNode w = ipa.get(i);
            List<String> issues = new ArrayList<>();
            for (JsonNode n : w.path("issues")) {
                issues.add(n.isTextual() ? n.asText() : n.toString());
            }
            // engine trả theo thứ tự từ đã gửi; có trường word thì ưu tiên
            String word = w.hasNonNull("word") ? w.get("word").asText() : i < words.size() ? words.get(i) : "";
            out.add(new WordScore(word, w.path("score").asDouble(), issues, w.path("expected_ipa").asText(null), w.path("heard_ipa").asText(null)));
        }
        JsonNode g = r.path("gopt").path("utterance");
        return new Analysis(out, g.isNumber() ? g.asDouble() : null);
    }

    private LinkedMultiValueMap<String, Object> form(Audio audio) {
        var form = new LinkedMultiValueMap<String, Object>();
        form.add("audio", new AudioPart(audio));
        return form;
    }

    private JsonNode post(String path, LinkedMultiValueMap<String, Object> form) throws SpeakingEngineException {
        if (baseUrl.isEmpty()) {
            throw new SpeakingEngineException(false, "Chưa cấu hình ENGINE_URL", null);
        }
        boolean acquired;
        try {
            acquired = slots.tryAcquire(waitForSlot.toSeconds(), TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new SpeakingEngineException(true, "Bị ngắt khi chờ engine", e);
        }
        if (!acquired) {
            throw new SpeakingEngineException(true, "Engine đang bận", null);
        }
        try {
            JsonNode body = http.post().uri(baseUrl + path).header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .contentType(MediaType.MULTIPART_FORM_DATA).body(form).retrieve().body(JsonNode.class);
            if (body == null) {
                throw new SpeakingEngineException(true, "Engine trả phản hồi rỗng", null);
            }
            return body;
        } catch (RestClientResponseException e) {
            int s = e.getStatusCode().value();
            throw new SpeakingEngineException(s == 429 || s >= 500, "Engine trả lỗi " + s, e);
        } catch (ResourceAccessException e) {
            throw new SpeakingEngineException(true, "Không kết nối được engine hoặc quá thời gian chờ", e);
        } catch (RuntimeException e) {
            // phản hồi không đọc được thành JSON
            throw new SpeakingEngineException(true, "Phản hồi engine không đọc được", e);
        } finally {
            slots.release();
        }
    }

    /** Luồng audio có tên file và độ dài để multipart không phải nạp hết vào bộ nhớ. */
    private static final class AudioPart extends InputStreamResource {

        private final long size;

        AudioPart(Audio audio) {
            super(() -> audio.open().get());
            this.size = audio.size();
        }

        @Override
        public String getFilename() { return "audio.wav"; }

        @Override
        public long contentLength() { return size; }
    }
}
