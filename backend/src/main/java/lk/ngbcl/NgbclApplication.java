package lk.ngbcl;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * NGBCL platform API — a modular monolith (SRS §10). Each top-level package is a module:
 * shared, security, identity, tournament, audit, realtime (M0) and, in later milestones,
 * files, registration, player, team, auction, competition, scoring, standings, content, reporting.
 * Modules talk through services, never through another module's repositories.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class NgbclApplication {

    public static void main(String[] args) {
        SpringApplication.run(NgbclApplication.class, args);
    }
}
