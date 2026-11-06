package com.pps.smartenglish.ai.provider;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.errors.AnthropicInvalidDataException;
import com.anthropic.errors.AnthropicIoException;
import com.anthropic.errors.AnthropicServiceException;
import com.anthropic.errors.RateLimitException;
import com.anthropic.models.messages.CacheControlEphemeral;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.StopReason;
import com.anthropic.models.messages.StructuredMessage;
import com.anthropic.models.messages.StructuredMessageCreateParams;
import com.anthropic.models.messages.TextBlockParam;
import com.anthropic.models.messages.ThinkingConfigDisabled;
import com.pps.smartenglish.ai.config.AiProperties;
import java.time.Duration;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Haiku 5.5 qua SDK chính thức. Không thử lại ở tầng SDK (maxRetries 0) vì worker thử lại có giãn cách và ghi usage từng lần.
 * Không gửi temperature/top_p/top_k, không prefill; tắt thinking để đạt 3-5 s (system-design 5.4).
 * shortcut: chưa stream từng đoạn (không có sự kiện delta), chờ phản hồi đầy đủ rồi mới trả.
 */
@Component
@ConditionalOnProperty(prefix = "app.ai", name = "provider", havingValue = "anthropic", matchIfMissing = true)
class AnthropicGradingProvider implements AiGradingProvider {

    static final String PROVIDER = "anthropic";

    private final AnthropicClient client;
    private final String model;

    AnthropicGradingProvider(AiProperties props) {
        if (props.apiKey() == null || props.apiKey().isBlank()) {
            throw new IllegalStateException("ANTHROPIC_API_KEY là bắt buộc khi AI_PROVIDER=anthropic");
        }
        this.model = props.model();
        this.client = AnthropicOkHttpClient.builder().apiKey(props.apiKey()).maxRetries(0)
                .timeout(Duration.ofSeconds(60)).build();
    }

    @Override
    public <T> AiResult<T> complete(AiCall<T> call) throws AiCallException {
        StructuredMessageCreateParams<T> params = MessageCreateParams.builder()
                .model(model)
                .maxTokens((long) call.maxTokens())
                .systemOfTextBlockParams(List.of(TextBlockParam.builder().text(call.systemPrompt())
                        .cacheControl(CacheControlEphemeral.builder().build()).build()))
                .addUserMessage(call.userPrompt())
                .thinking(ThinkingConfigDisabled.builder().build())
                .outputConfig(call.resultType())
                .build();
        StructuredMessage<T> message;
        try {
            message = client.messages().create(params);
        } catch (RateLimitException | AnthropicIoException e) {
            throw new AiCallException(true, "Nhà cung cấp AI tạm thời không phản hồi", null, e);
        } catch (AnthropicServiceException e) {
            int status = e.statusCode();
            throw new AiCallException(status >= 500 || status == 408 || status == 409,
                    "Nhà cung cấp AI trả lỗi " + status, null, e);
        } catch (AnthropicInvalidDataException e) {
            throw new AiCallException(true, "Phản hồi AI không đọc được", null, e);
        }

        AiUsage usage = usageOf(message);
        StopReason stop = message.stopReason().orElse(null);
        if (StopReason.REFUSAL.equals(stop)) {
            throw new AiCallException(false, "Mô hình từ chối trả lời", usage, null);
        }
        if (StopReason.MAX_TOKENS.equals(stop)) {
            throw new AiCallException(false, "Phản hồi bị cắt do chạm trần token", usage, null);
        }
        T data = message.content().stream().flatMap(b -> b.text().stream()).map(t -> t.text()).findFirst().orElse(null);
        if (data == null) {
            throw new AiCallException(true, "Phản hồi AI không có nội dung", usage, null);
        }
        return new AiResult<>(data, usage);
    }

    private AiUsage usageOf(StructuredMessage<?> m) {
        var u = m.usage();
        return new AiUsage(PROVIDER, model, (int) u.inputTokens(), (int) u.outputTokens(),
                u.cacheReadInputTokens().orElse(0L).intValue(), u.cacheCreationInputTokens().orElse(0L).intValue());
    }
}
