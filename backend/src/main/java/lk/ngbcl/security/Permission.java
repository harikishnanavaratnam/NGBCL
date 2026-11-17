package lk.ngbcl.security;

/**
 * Fine-grained permissions granted to roles (seeded in V1__foundation.sql).
 * Checked with {@code @PreAuthorize("hasAuthority('...')")} on every service/controller method.
 * Mirrored (for UI gating only) in frontend src/lib/auth/permissions.ts.
 */
public enum Permission {
    TOURNAMENT_VIEW,
    TOURNAMENT_MANAGE,
    SETTINGS_MANAGE,
    REGISTRATION_VIEW,
    REGISTRATION_REVIEW,
    DIRECTORY_MANAGE,
    CATEGORY_MANAGE,
    AUCTION_CONFIGURE,
    AUCTION_CONTROL,
    AUCTION_CORRECT,
    FIXTURE_MANAGE,
    MATCH_SCORE,
    MATCH_SCORE_ANY,
    RESULT_CONFIRM,
    RESULT_CORRECT,
    STANDINGS_MANAGE,
    CONTENT_MANAGE,
    USER_MANAGE,
    AUDIT_VIEW,
    REPORT_VIEW,
    FILE_UPLOAD
}
