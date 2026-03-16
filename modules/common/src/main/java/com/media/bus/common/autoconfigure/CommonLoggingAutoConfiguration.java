package com.media.bus.common.autoconfigure;

import com.media.bus.common.logging.MdcLoggingFilter;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;

@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class CommonLoggingAutoConfiguration {

    /**
     * MDC 로깅 필터를 Spring Security(order=-100) 이후에 등록한다.
     * order=0 이므로 SecurityContextHolder에서 인증 정보를 읽을 수 있다.
     */
    @Bean
    public FilterRegistrationBean<MdcLoggingFilter> mdcLoggingFilter() {
        FilterRegistrationBean<MdcLoggingFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new MdcLoggingFilter());
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 200); // Security(-100) 이후
        registration.addUrlPatterns("/*");
        return registration;
    }
}
