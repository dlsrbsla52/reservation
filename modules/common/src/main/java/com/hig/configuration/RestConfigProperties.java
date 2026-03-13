package com.hig.configuration;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Rest Service 관련 Properties
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "rest.service")
public class RestConfigProperties {
	
	private String[] applyPatterns;

}
