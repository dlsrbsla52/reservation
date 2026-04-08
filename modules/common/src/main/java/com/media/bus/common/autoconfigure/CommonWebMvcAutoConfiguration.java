package com.media.bus.common.autoconfigure;

import com.media.bus.common.web.advisor.ExceptionAdvisor;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class CommonWebMvcAutoConfiguration {

    @Bean
    public ExceptionAdvisor exceptionAdvisor() {
        return new ExceptionAdvisor();
    }
}
