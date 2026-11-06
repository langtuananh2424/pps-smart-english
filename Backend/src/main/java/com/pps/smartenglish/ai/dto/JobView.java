package com.pps.smartenglish.ai.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;

/** result chỉ có khi DONE, error chỉ có khi FAILED. quotaRefunded: job lỗi hẳn đã được hoàn lượt. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record JobView(Long id, String kind, String status, JsonNode result, String error, boolean quotaRefunded) {}
