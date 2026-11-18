package lk.ngbcl.registration.domain;

public enum DuplicateResolution {
    OPEN,
    /** Same person: link the registration to the existing player profile. */
    LINKED,
    /** Same details but a different person (e.g. shared family phone): create a new profile. */
    NEW_PROFILE,
    NOT_DUPLICATE
}
