package com.helmifr.library.common;

import org.springframework.data.web.config.PageableHandlerMethodArgumentResolverCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Caps ?size= so a client cannot ask for an unbounded page. */
@Configuration
public class PagingConfig {

    public static final int MAX_PAGE_SIZE = 100;

    @Bean
    PageableHandlerMethodArgumentResolverCustomizer pageSizeLimit() {
        return resolver -> resolver.setMaxPageSize(MAX_PAGE_SIZE);
    }
}
