package com.media.bus.common.autoconfigure;

import com.media.bus.common.configuration.BulkheadProperties;
import com.media.bus.common.core.aop.AsyncMdcAspect;
import com.media.bus.common.core.aop.BoundedConcurrencyAspect;
import com.media.bus.common.core.aop.TransactionalBulkheadAspect;
import io.github.resilience4j.bulkhead.BulkheadRegistry;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@ConditionalOnClass(Aspect.class)
@EnableConfigurationProperties(BulkheadProperties.class)
public class CommonCoreAutoConfiguration {

    @Bean
    public AsyncMdcAspect asyncMdcAspect() {
        return new AsyncMdcAspect();
    }

    @Bean
    public BoundedConcurrencyAspect boundedConcurrencyAspect(ApplicationContext applicationContext) {
        return new BoundedConcurrencyAspect(applicationContext);
    }

    @Bean
    public TransactionalBulkheadAspect transactionalBulkheadAspect(BulkheadRegistry bulkheadRegistry, BulkheadProperties bulkheadProperties) {
        return new TransactionalBulkheadAspect(bulkheadRegistry, bulkheadProperties);
    }
}