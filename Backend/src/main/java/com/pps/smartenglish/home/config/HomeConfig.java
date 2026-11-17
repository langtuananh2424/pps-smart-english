package com.pps.smartenglish.home.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({CamProperties.class, ExerciseStatsProperties.class})
class HomeConfig {}
