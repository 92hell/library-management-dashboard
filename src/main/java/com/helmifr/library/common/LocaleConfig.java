package com.helmifr.library.common;

import java.util.List;
import java.util.Locale;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.i18n.AcceptHeaderLocaleResolver;

/** The app speaks English (default) and Bahasa Indonesia; the frontend sends its language as Accept-Language. */
@Configuration
public class LocaleConfig {

    public static final Locale INDONESIAN = Locale.forLanguageTag("id");

    @Bean
    LocaleResolver localeResolver() {
        AcceptHeaderLocaleResolver resolver = new AcceptHeaderLocaleResolver();
        resolver.setSupportedLocales(List.of(Locale.ENGLISH, INDONESIAN));
        resolver.setDefaultLocale(Locale.ENGLISH);
        return resolver;
    }
}
