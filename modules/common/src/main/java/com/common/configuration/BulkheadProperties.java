package com.common.configuration;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Data
@Validated
@ConfigurationProperties(prefix = "hig.bulkhead")
// @Configuration 대신 CommonCoreAutoConfiguration의 @EnableConfigurationProperties로 등록.
// 라이브러리 모듈은 컴포넌트 스캔 범위가 보장되지 않으므로 auto-configuration이 직접 bean을 등록한다.
public class BulkheadProperties {

    @NotBlank(message = "hig.bulkhead.database-name 은 반드시 설정해야 합니다. (resilience4j.bulkhead.instances 이름과 일치해야 함)")
    private String databaseName;
}
