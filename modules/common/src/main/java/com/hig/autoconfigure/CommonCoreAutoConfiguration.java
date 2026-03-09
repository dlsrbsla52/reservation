package com.hig.autoconfigure;

import com.hig.core.aop.BoundedConcurrencyAspect;
import com.hig.core.aop.TransactionalBulkheadAspect;
import io.github.resilience4j.bulkhead.BulkheadRegistry;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@ConditionalOnClass(Aspect.class)
public class CommonCoreAutoConfiguration {

    @Bean
    public BoundedConcurrencyAspect boundedConcurrencyAspect(ApplicationContext applicationContext) {
        return new BoundedConcurrencyAspect(applicationContext);
    }

    @Bean
    public TransactionalBulkheadAspect transactionalBulkheadAspect(BulkheadRegistry bulkheadRegistry) {
        return new TransactionalBulkheadAspect(bulkheadRegistry);
    }
}
