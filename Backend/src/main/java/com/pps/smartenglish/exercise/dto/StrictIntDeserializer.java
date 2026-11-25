package com.pps.smartenglish.exercise.dto;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import java.io.IOException;

/** Chỉ nhận số nguyên JSON thật: Jackson mặc định cắt 70.5 thành 70 và nhận cả chuỗi "70". Dùng riêng cho trường cần chặt. */
public class StrictIntDeserializer extends JsonDeserializer<Integer> {

    @Override
    public Integer deserialize(JsonParser p, DeserializationContext ctx) throws IOException {
        if (p.currentToken() != JsonToken.VALUE_NUMBER_INT) {
            return (Integer) ctx.handleUnexpectedToken(Integer.class, p);
        }
        return p.getIntValue();
    }
}
