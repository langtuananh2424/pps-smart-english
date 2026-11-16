package com.pps.smartenglish.ai.speaking;

import com.pps.smartenglish.ai.speaking.EngineModels.Analysis;
import com.pps.smartenglish.ai.speaking.EngineModels.Audio;
import com.pps.smartenglish.ai.speaking.EngineModels.Transcript;
import java.util.List;

/** Cổng tới pps-speaking-engine (Whisper + GOPT/wav2vec2). Địa chỉ chỉ lấy từ cấu hình. */
public interface SpeakingEngineClient {

    /** /v1/transcribe: bản chép lời tiếng Anh kèm thời gian từng từ. */
    Transcript transcribe(Audio audio) throws SpeakingEngineException;

    /** /v1/analyze: điểm phát âm theo từng từ mong đợi. */
    Analysis analyze(Audio audio, List<String> words) throws SpeakingEngineException;
}
