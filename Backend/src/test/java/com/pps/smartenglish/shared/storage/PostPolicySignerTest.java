package com.pps.smartenglish.shared.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.Base64;
import org.junit.jupiter.api.Test;

class PostPolicySignerTest {

    /** Vector công khai của AWS (tài liệu SigV4, ví dụ IAM): cùng thuật toán dẫn xuất khóa dùng cho POST policy. */
    @Test
    void matchesAwsPublishedVector() {
        String toSign = "AWS4-HMAC-SHA256\n20150830T123600Z\n20150830/us-east-1/iam/aws4_request\n"
                + "f536975d06c0309214f805bb90ccff089219ecd68b2577efef23edd43b7e1a59";
        assertEquals("5d672d79c15b13162d9279b0855cfba6789a8edb4c82c400e06b5924a6f2b5d7",
                PostPolicySigner.signature("wJalrXUtnFEMI/K7MDENG+bPxRfiCYEXAMPLEKEY", "20150830", "us-east-1", "iam", toSign));
    }

    @Test
    void presignPostCarriesAllConditionsAndValidSignature() throws Exception {
        var s3 = new S3ObjectStorage("http://minio:9000", "http://public:9100/", "AKIAEXAMPLE", "secret", "bkt");
        var form = s3.presignPost("speaking/1/a.wav", "audio/wav", 1000, Duration.ofMinutes(10));
        assertEquals("http://public:9100/bkt", form.url());
        var f = form.fields();
        var policy = new ObjectMapper().readTree(Base64.getDecoder().decode(f.get("policy")));
        String conditions = policy.get("conditions").toString();
        assertTrue(conditions.contains("{\"bucket\":\"bkt\"}"));
        assertTrue(conditions.contains("{\"key\":\"speaking/1/a.wav\"}"));
        assertTrue(conditions.contains("[\"content-length-range\",1,1000]"));
        assertTrue(conditions.contains("{\"Content-Type\":\"audio/wav\"}"));
        assertEquals(PostPolicySigner.signature("secret", f.get("x-amz-date").substring(0, 8), "us-east-1", "s3", f.get("policy")),
                f.get("x-amz-signature"));
        assertEquals("speaking/1/a.wav", f.get("key"));
    }
}
