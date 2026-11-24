package com.pps.smartenglish.exercise.bulk;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(ImportProperties.class)
class ImportConfig {}
