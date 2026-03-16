package com.media.bus.common.autoconfigure;

import com.media.bus.common.configuration.RestConfigProperties;
import com.media.bus.common.web.advisor.ExceptionAdvisor;
import com.media.bus.common.web.advisor.ResponseAdvisor;
import com.media.bus.common.web.wrapper.DefaultObjectBodyWrapper;
import com.media.bus.common.web.wrapper.NullBodyWrapper;
import com.media.bus.common.web.wrapper.PageResultBodyWrapper;
import com.media.bus.common.web.wrapper.PassthroughBodyWrapper;
import com.media.bus.common.web.wrapper.ResponseBodyWrapper;
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
