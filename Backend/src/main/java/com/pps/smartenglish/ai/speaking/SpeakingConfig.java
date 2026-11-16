package com.pps.smartenglish.ai.speaking;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(SpeakingProperties.class)
class SpeakingConfig {}
