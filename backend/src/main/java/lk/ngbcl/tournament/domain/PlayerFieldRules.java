package lk.ngbcl.tournament.domain;

/**
 * Configurable player-form fields (stored as jsonb). Full name, email, phone, photo and the
 * declaration are always collected and required, so they aren't listed here. Missing entries
 * fall back to the defaults below.
 */
public record PlayerFieldRules(
        FieldRule dateOfBirth,
        FieldRule gender,
        FieldRule address,
        FieldRule homeArea,
        FieldRule experience,
        FieldRule emergencyContact) {

    public PlayerFieldRules {
        dateOfBirth = or(dateOfBirth, true);
        gender = or(gender, true);
        address = or(address, false);
        homeArea = or(homeArea, true);
        experience = or(experience, false);
        emergencyContact = or(emergencyContact, false);
    }

    public static PlayerFieldRules defaults() {
        return new PlayerFieldRules(null, null, null, null, null, null);
    }

    private static FieldRule or(FieldRule rule, boolean requiredByDefault) {
        return rule != null ? rule : new FieldRule(true, requiredByDefault);
    }
}
