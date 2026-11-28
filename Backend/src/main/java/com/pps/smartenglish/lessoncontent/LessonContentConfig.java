package com.pps.smartenglish.lessoncontent;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(LessonContentProperties.class)
class LessonContentConfig {}
