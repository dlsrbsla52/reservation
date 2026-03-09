package com.hig.autoconfigure;

import com.hig.mvc.advisor.ExceptionAdvisor;
import com.hig.mvc.advisor.ResponseAdvisor;
import com.hig.mvc.properties.RestConfigProperties;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@EnableConfigurationProperties(RestConfigProperties.class)
public class CommonWebMvcAutoConfiguration {

    @Bean
    public ExceptionAdvisor exceptionAdvisor() {
        return new ExceptionAdvisor();
    }

    @Bean
    public ResponseAdvisor responseAdvisor(RestConfigProperties restConfigProperties) {
        return new ResponseAdvisor(restConfigProperties);
    }
}
