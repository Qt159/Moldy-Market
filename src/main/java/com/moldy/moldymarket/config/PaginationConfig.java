package com.moldy.moldymarket.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

@Configuration
public class PaginationConfig implements WebMvcConfigurer {

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        PageableHandlerMethodArgumentResolver resolver = new PageableHandlerMethodArgumentResolver();

        // Client gửi ?page = 1 là trang đầu
        resolver.setOneIndexedParameters(true);

        // Giới hạn size, tránh request ?size=99999
        resolver.setMaxPageSize(MAX_PAGE_SIZE);

        // Default nếu không gửi page/size
        resolver.setFallbackPageable(PageRequest.of(0, DEFAULT_PAGE_SIZE));

        resolvers.add(resolver);
    }
}
