package lk.ngbcl.registration.service;

import jakarta.persistence.EntityManager;
import java.util.UUID;
import lk.ngbcl.registration.domain.RegistrationKind;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Gap-tolerant, collision-free reference numbers: PLY-2026-00142 (PLY-007, TEM-005). */
@Service
public class ReferenceNumberService {

    private final EntityManager em;

    public ReferenceNumberService(EntityManager em) {
        this.em = em;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public String next(UUID tournamentId, RegistrationKind kind, int year) {
        // Row lock via upsert; concurrent submissions serialise on this row only.
        Number value = (Number) em.createNativeQuery("""
                INSERT INTO reference_sequences (tournament_id, kind, next_value) VALUES (:t, :k, 2)
                ON CONFLICT (tournament_id, kind) DO UPDATE SET next_value = reference_sequences.next_value + 1
                RETURNING next_value - 1
                """)
                .setParameter("t", tournamentId)
                .setParameter("k", kind.name())
                .getSingleResult();
        return format(kind, year, value.intValue());
    }

    static String format(RegistrationKind kind, int year, int n) {
        return kind.prefix + "-" + year + "-" + String.format("%0" + kind.digits + "d", n);
    }
}
