package lk.ngbcl.registration.domain;

public enum RegistrationKind {
    PLAYER("PLY", 5),
    TEAM("TEM", 4);

    public final String prefix;
    public final int digits;

    RegistrationKind(String prefix, int digits) {
        this.prefix = prefix;
        this.digits = digits;
    }
}
