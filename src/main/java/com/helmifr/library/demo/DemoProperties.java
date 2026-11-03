package com.helmifr.library.demo;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Demo mode (library.demo.*): seeds sample data plus a demo customer and demo librarian, and
 * shows their credentials on the login page. Meant for a public portfolio demo, not production.
 */
@ConfigurationProperties(prefix = "library.demo")
public record DemoProperties(boolean enabled, String password) {

    public String customerUsername() {
        return DemoDataSeeder.DEMO_CUSTOMER;
    }

    public String customerPassword() {
        return password;
    }

    public String librarianUsername() {
        return DemoDataSeeder.DEMO_LIBRARIAN;
    }

    public String librarianPassword() {
        return password;
    }
}
