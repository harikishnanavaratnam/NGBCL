package lk.ngbcl.files.domain;

/** Human review: only VERIFIED assets may be published (BR-020, PLY-025, TEM-007). */
public enum VerificationStatus {
    UNVERIFIED,
    VERIFIED,
    REJECTED
}
