package com.helmifr.library.demo;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/** Seeds demo data at startup when library.demo.enabled=true. */
@Component
@Order(2)
@ConditionalOnProperty(prefix = "library.demo", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
public class DemoDataRunner implements ApplicationRunner {

    private final DemoDataSeeder seeder;

    @Override
    public void run(ApplicationArguments args) {
        seeder.seed();
    }
}
