package com.pps.smartenglish.suggestion.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(SuggestionProperties.class)
class SuggestionConfig {}
