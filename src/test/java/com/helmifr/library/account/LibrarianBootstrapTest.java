package com.helmifr.library.account;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.helmifr.library.security.SecurityProperties;
import com.helmifr.library.support.IntegrationTest;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@IntegrationTest
@ExtendWith(OutputCaptureExtension.class)
@DisplayName("First librarian (bootstrap)")
class LibrarianBootstrapTest {

    @Autowired UserAccountRepository accounts;
    @Autowired AccountService accountService;
    @Autowired PasswordEncoder passwordEncoder;

    private LibrarianBootstrap bootstrap(String username, String password) {
        return new LibrarianBootstrap(new SecurityProperties(new SecurityProperties.Account(username, password)),
                accounts, accountService, passwordEncoder);
    }

    @Test
    @DisplayName("a generated password is shown on the console once, never written to the log files")
    void generatedPasswordNotLogged(CapturedOutput output) {
        Logger logger = (Logger) LoggerFactory.getLogger(LibrarianBootstrap.class);
        ListAppender<ILoggingEvent> logged = new ListAppender<>();
        logged.start();
        logger.addAppender(logged);
        try {
            bootstrap("fresh-librarian", "").run(null);
        } finally {
            logger.detachAppender(logged);
        }

        Matcher m = Pattern.compile("password: (\\S+)").matcher(output.getOut());
        assertThat(m.find()).as("password printed to the console").isTrue();
        String password = m.group(1);
        assertThat(passwordEncoder.matches(password,
                accounts.findByUsernameIgnoreCase("fresh-librarian").orElseThrow().getPasswordHash())).isTrue();
        assertThat(logged.list).isNotEmpty()
                .allSatisfy(e -> assertThat(e.getFormattedMessage()).doesNotContain(password));
    }

    @Test
    @DisplayName("on start-up the configured librarian is re-enabled and gets the configured password (recovery)")
    void recoversLockedOutOwner() {
        UserAccount owner = accounts.findByUsernameIgnoreCase("librarian").orElseThrow();
        owner.setEnabled(false);
        owner.setPasswordHash(passwordEncoder.encode("set-by-an-intruder"));

        bootstrap("librarian", "recovery-password-123").run(null);

        UserAccount recovered = accounts.findByUsernameIgnoreCase("librarian").orElseThrow();
        assertThat(recovered.isEnabled()).isTrue();
        assertThat(recovered.getRole()).isEqualTo(Role.LIBRARIAN);
        assertThat(passwordEncoder.matches("recovery-password-123", recovered.getPasswordHash())).isTrue();
    }
}
