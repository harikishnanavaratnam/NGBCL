package lk.ngbcl.registration.service;

import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import lk.ngbcl.player.service.PlayerService;
import lk.ngbcl.registration.domain.DuplicateResolution;
import lk.ngbcl.registration.domain.PlayerRegistration;
import lk.ngbcl.registration.domain.PlayerRegistrationRepository;
import lk.ngbcl.registration.domain.RegistrationDuplicate;
import lk.ngbcl.registration.domain.RegistrationDuplicateRepository;
import lk.ngbcl.registration.domain.RegistrationStatus;
import lk.ngbcl.tournament.domain.TournamentSettings;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Flags possible duplicates (PLY-010/011) against other registrations in this edition and
 * existing player profiles, using the matching rules configured in settings. Never merges.
 */
@Component
public class DuplicateDetector {

    private final PlayerRegistrationRepository registrations;
    private final RegistrationDuplicateRepository duplicates;
    private final PlayerService players;
    private final Clock clock;

    public DuplicateDetector(PlayerRegistrationRepository registrations, RegistrationDuplicateRepository duplicates,
            PlayerService players, Clock clock) {
        this.registrations = registrations;
        this.duplicates = duplicates;
        this.players = players;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public int detect(PlayerRegistration r, TournamentSettings s) {
        Map<UUID, String> regMatches = new LinkedHashMap<>();
        if (s.isDupMatchEmail()) {
            registrations.findByTournamentIdAndEmailAndIdNot(r.getTournamentId(), r.getEmail(), r.getId())
                    .forEach(o -> merge(regMatches, o, "email"));
        }
        if (s.isDupMatchPhone()) {
            registrations.findByTournamentIdAndPhoneAndIdNot(r.getTournamentId(), r.getPhone(), r.getId())
                    .forEach(o -> merge(regMatches, o, "phone"));
        }
        if (s.isDupMatchNameDob() && r.getDateOfBirth() != null) {
            registrations.findByNameAndDob(r.getTournamentId(), r.getId(), r.getFullName().toLowerCase(Locale.ROOT),
                    r.getDateOfBirth()).forEach(o -> merge(regMatches, o, "name+dob"));
        }
        List<PlayerService.Candidate> profileMatches = players.candidates(r.getEmail(), r.getPhone(), r.getFullName(),
                r.getDateOfBirth(), s.isDupMatchEmail(), s.isDupMatchPhone(), s.isDupMatchNameDob());

        int found = 0;
        for (var e : regMatches.entrySet()) {
            duplicates.save(new RegistrationDuplicate(r.getId(), e.getKey(), null, e.getValue(), clock.instant()));
            found++;
        }
        for (PlayerService.Candidate c : profileMatches) {
            duplicates.save(new RegistrationDuplicate(r.getId(), null, c.playerId(), c.matchedOn(), clock.instant()));
            found++;
        }
        r.flagDuplicate(found > 0);
        return found;
    }

    /** Recomputes the flag after a reviewer resolves a match. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void refreshFlag(PlayerRegistration r) {
        duplicates.flush();
        r.flagDuplicate(duplicates.countByRegistrationIdAndResolution(r.getId(), DuplicateResolution.OPEN) > 0);
    }

    private static void merge(Map<UUID, String> m, PlayerRegistration other, String on) {
        // Approved registrations are covered by their player profile match; withdrawn ones are noise.
        if (other.getStatus() == RegistrationStatus.WITHDRAWN || other.getStatus() == RegistrationStatus.APPROVED) {
            return;
        }
        m.merge(other.getId(), on, (a, b) -> a + "," + b);
    }
}
