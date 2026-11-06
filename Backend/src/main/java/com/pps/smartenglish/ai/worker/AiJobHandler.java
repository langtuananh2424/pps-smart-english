package com.pps.smartenglish.ai.worker;

import com.pps.smartenglish.ai.domain.AiJob;
import com.pps.smartenglish.ai.domain.AiJobKind;
import com.pps.smartenglish.ai.provider.AiCallException;
import com.pps.smartenglish.ai.provider.AiUsage;
import java.util.function.Consumer;

/** Strategy xử lý một loại job. execute chạy NGOÀI transaction (có gọi mạng); effects chạy trong transaction hoàn tất job. */
public interface AiJobHandler {

    AiJobKind kind();

    Completion execute(AiJob job) throws AiCallException;

    /** output được lưu vào ai_jobs.output; effects chỉ chạy nếu job còn đúng lượt thử (ghi có điều kiện thành công). */
    record Completion(Object output, AiUsage usage, Consumer<AiJob> effects) {

        public static Completion of(Object output, AiUsage usage) {
            return new Completion(output, usage, job -> {});
        }
    }
}
