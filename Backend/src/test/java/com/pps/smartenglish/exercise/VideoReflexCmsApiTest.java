package com.pps.smartenglish.exercise;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/** D-158: Video phản xạ cần nhóm có video; mốc at tăng dần theo thứ tự câu và nhỏ hơn thời lượng video. */
class VideoReflexCmsApiTest extends ListeningTestSupport {

    private static final String CMS = "/api/admin/cms";

    private ResultActions call(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder req, Account a, Object body) throws Exception {
        return send(req.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)), a.accessToken());
    }

    private long group(Account admin, long bank, boolean video) throws Exception {
        var body = new HashMap<String, Object>(Map.of("title", "Video"));
        if (video) {
            body.put("videoYoutube", "https://youtu.be/dQw4w9WgXcQ");
            body.put("videoDurationSec", 120);
        }
        JsonNode g = json.readTree(call(MockMvcRequestBuilders.post(CMS + "/banks/" + bank + "/groups"), admin, body)
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        return g.get("id").asLong();
    }

    private ResultActions create(Account admin, long bank, Long group, String type, int at) throws Exception {
        var body = new HashMap<String, Object>(Map.of("type", type, "prompt", "Câu", "payload", Map.of("at", at, "prompt", "What did you do?")));
        if (group != null) {
            body.put("groupId", group);
        }
        return call(MockMvcRequestBuilders.post(CMS + "/banks/" + bank + "/questions"), admin, body);
    }

    @Test
    void needsGroupWithVideo() throws Exception {
        var admin = withRole("ADMIN");
        long bank = world("FREE", null).bank();
        create(admin, bank, null, "VIDEO_SHORT", 10).andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.details.groupId").exists());
        create(admin, bank, group(admin, bank, false), "VIDEO_SHORT", 10).andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.details.groupId").exists());
        create(admin, bank, group(admin, bank, true), "VIDEO_SHORT", 10).andExpect(status().isCreated());
    }

    @Test
    void marksIncreaseAndStayInsideVideo() throws Exception {
        var admin = withRole("ADMIN");
        long bank = world("FREE", null).bank();
        long g = group(admin, bank, true);
        create(admin, bank, g, "VIDEO_SHORT", 30).andExpect(status().isCreated());
        create(admin, bank, g, "VIDEO_PICTURE", 30).andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.details['payload.at']").exists());
        create(admin, bank, g, "VIDEO_PICTURE", 20).andExpect(status().isBadRequest());
        create(admin, bank, g, "VIDEO_PICTURE", 120).andExpect(status().isBadRequest()); // bằng thời lượng
        create(admin, bank, g, "VIDEO_PICTURE", 60).andExpect(status().isCreated());
        // rút ngắn video dưới mốc đã có thì bị chặn
        call(MockMvcRequestBuilders.patch(CMS + "/groups/" + g), admin, Map.of("videoDurationSec", 50)).andExpect(status().isBadRequest());
        call(MockMvcRequestBuilders.patch(CMS + "/groups/" + g), admin, Map.of("videoDurationSec", 90)).andExpect(status().isOk());
    }
}
