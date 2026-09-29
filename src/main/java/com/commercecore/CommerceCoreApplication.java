package com.commercecore;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * CommerceCore bootstrap entry point.
 *
 * Modular-monolith bootstrap entry point. See PROJECT_STATE.md and
 * NEXT_TASK.md for the currently verified scope.
 */
@SpringBootApplication
@EnableScheduling
public class CommerceCoreApplication {

    public static void main(String[] args) {
        SpringApplication.run(CommerceCoreApplication.class, args);
    }
}
