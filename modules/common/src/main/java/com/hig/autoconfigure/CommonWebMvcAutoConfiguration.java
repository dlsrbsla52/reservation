package com.hig.autoconfigure;

import com.hig.mvc.advisor.ExceptionAdvisor;
import com.hig.mvc.advisor.ResponseAdvisor;
import com.hig.mvc.properties.RestConfigProperties;
import com.hig.mvc.wrapper.DefaultObjectBodyWrapper;
import com.hig.mvc.wrapper.NullBodyWrapper;
import com.hig.mvc.wrapper.PageResultBodyWrapper;
import com.hig.mvc.wrapper.PassthroughBodyWrapper;
import com.hig.mvc.wrapper.ResponseBodyWrapper;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import java.util.List;

@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@EnableConfigurationProperties(RestConfigProperties.class)
public class CommonWebMvcAutoConfiguration {

    @Bean
    public PassthroughBodyWrapper passthroughBodyWrapper() {
        return new PassthroughBodyWrapper();
    }

    @Bean
    public NullBodyWrapper nullBodyWrapper() {
        return new NullBodyWrapper();
    }

    @Bean
    public PageResultBodyWrapper pageResultBodyWrapper() {
        return new PageResultBodyWrapper();
    }

    @Bean
    public DefaultObjectBodyWrapper defaultObjectBodyWrapper() {
        return new DefaultObjectBodyWrapper();
    }

    @Bean
    public ExceptionAdvisor exceptionAdvisor() {
        return new ExceptionAdvisor();
    }

    @Bean
    public ResponseAdvisor responseAdvisor(RestConfigProperties restConfigProperties,
                                           List<ResponseBodyWrapper> bodyWrappers) {
        return new ResponseAdvisor(restConfigProperties, bodyWrappers);
    }
}
