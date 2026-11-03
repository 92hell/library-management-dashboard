package com.helmifr.library.common;

import java.time.Duration;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Vite puts a content hash in every built asset's file name (assets/index-3f9a1c.js), so a file
 * never changes under the same name: browsers may keep it for a year. index.html is not covered
 * here and stays "no-cache" (Spring Security's default), so each deploy is picked up immediately.
 */
@Configuration
public class StaticAssetsConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/assets/**")
                .addResourceLocations("classpath:/static/assets/")
                .setCacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable());
    }
}
