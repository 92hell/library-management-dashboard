package com.helmifr.library.demo;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** In demo mode: resets the demo on start-up and on library.demo.reset-cron (nightly by default). */
@Component
@Order(2)
@ConditionalOnProperty(prefix = "library.demo", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
public class DemoDataRunner implements ApplicationRunner {

    private final DemoDataSeeder seeder;

    @Override
    public void run(ApplicationArguments args) {
        seeder.reset();
    }

    @Scheduled(cron = "${library.demo.reset-cron}")
    public void nightlyReset() {
        seeder.reset();
    }
}
