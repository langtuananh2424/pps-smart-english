package com.pps.smartenglish.exercise.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(RegradeProperties.class)
class RegradeConfig {}
