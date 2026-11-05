package com.pps.smartenglish.profile.dto;

import com.pps.smartenglish.profile.domain.SelfLevel;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Trường null = giữ nguyên. */
public record UpdateMeRequest(
        @Size(max = 100, message = "Tên hiển thị tối đa 100 ký tự")
        @Pattern(regexp = ".*\\S.*", message = "Tên hiển thị không được để trống")
        String displayName,
        SelfLevel selfLevel) {}
