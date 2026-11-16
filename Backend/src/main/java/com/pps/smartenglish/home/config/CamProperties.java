package com.pps.smartenglish.home.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** app.cam.*: số ngày trước hạn thì Cam nhắc khóa sắp hết hạn (D-53). */
@ConfigurationProperties(prefix = "app.cam")
public record CamProperties(@DefaultValue("7") int expiringDays) {}
