package com.project.payflow.global.config;

import com.project.payflow.global.filter.MdcLoggingFilter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

@Configuration
public class FilterConfig{

    @Bean
    public FilterRegistrationBean<MdcLoggingFilter> mdcLoggingFilter(){
        FilterRegistrationBean<MdcLoggingFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new MdcLoggingFilter());
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE); // Security 필터보다 먼저 실행됨을 보장
        return registration;
    }
}
