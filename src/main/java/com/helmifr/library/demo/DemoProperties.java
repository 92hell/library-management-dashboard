package com.helmifr.library.demo;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Demo mode (library.demo.*), for a public portfolio demo - not for real users:
 * <ul>
 *   <li>sample data plus a demo customer and demo librarian, whose logins are shown on the login page;</li>
 *   <li>the demo accounts cannot administer accounts or change their password or profile;</li>
 *   <li>registration is off, so no real personal data is collected;</li>
 *   <li>everything is reset to the sample data on start-up and on {@code resetCron} (nightly).</li>
 * </ul>
 */
@ConfigurationProperties(prefix = "library.demo")
public record DemoProperties(boolean enabled, String password, String resetCron) {

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

    /** True for the publicly known demo logins while demo mode is on. */
    public boolean isDemoAccount(String username) {
        return enabled && username != null
                && (username.equalsIgnoreCase(DemoDataSeeder.DEMO_CUSTOMER)
                        || username.equalsIgnoreCase(DemoDataSeeder.DEMO_LIBRARIAN));
    }
}
