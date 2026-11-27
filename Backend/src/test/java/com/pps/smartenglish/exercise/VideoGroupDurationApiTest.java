package com.pps.smartenglish.exercise;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/** D-159: thời lượng video do máy chủ giữ. Nhóm YouTube có câu VIDEO_CONNECTIVE bắt buộc có thời lượng CMS nhập. */
class VideoGroupDurationApiTest extends ListeningTestSupport {

    private static final String CMS = "/api/admin/cms";
    private static final String YT = "dQw4w9WgXcQ";

    private ResultActions call(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder req, Account a, Object body)
            throws Exception {
        return send(req.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)), a.accessToken());
    }

    private Map<String, Object> question(long group) {
        return Map.of("type", "VIDEO_CONNECTIVE", "prompt", "Chọn từ nối", "groupId", group, "choices", List.of(
                Map.of("label", "A", "content", "And", "isCorrect", false), Map.of("label", "B", "content", "However", "isCorrect", true)));
    }

    private long youtubeGroup(Account admin, long bank, Integer durationSec) throws Exception {
        var body = new HashMap<String, Object>(Map.of("title", "Video", "videoYoutube", "https://youtu.be/" + YT));
        body.put("videoDurationSec", durationSec);
        JsonNode g = json.readTree(call(MockMvcRequestBuilders.post(CMS + "/banks/" + bank + "/groups"), admin, body)
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        return g.get("id").asLong();
    }

    @Test
    void youtubeGroupWithVideoQuestionsNeedsDuration() throws Exception {
        var admin = withRole("ADMIN");
        var w = world("FREE", null);
        long noDuration = youtubeGroup(admin, w.bank(), null);
        call(MockMvcRequestBuilders.post(CMS + "/banks/" + w.bank() + "/questions"), admin, question(noDuration))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.details.videoDurationSec").exists());

        long group = youtubeGroup(admin, w.bank(), 95);
        call(MockMvcRequestBuilders.post(CMS + "/banks/" + w.bank() + "/questions"), admin, question(group)).andExpect(status().isCreated());

        // khoảng hợp lệ, và không được xóa thời lượng khi còn câu video
        call(MockMvcRequestBuilders.patch(CMS + "/groups/" + group), admin, Map.of("videoDurationSec", 0)).andExpect(status().isBadRequest());
        call(MockMvcRequestBuilders.patch(CMS + "/groups/" + group), admin, Map.of("videoDurationSec", 999999)).andExpect(status().isBadRequest());
        var clear = new HashMap<String, Object>();
        clear.put("videoDurationSec", null);
        call(MockMvcRequestBuilders.patch(CMS + "/groups/" + group), admin, clear).andExpect(status().isBadRequest());
        call(MockMvcRequestBuilders.patch(CMS + "/groups/" + group), admin, Map.of("videoDurationSec", 120)).andExpect(status().isOk())
                .andExpect(jsonPath("$.videoDurationSec").value(120));
    }
}
