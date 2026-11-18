package lk.ngbcl.tournament.domain;

/** Whether an optional registration field is shown, and whether it must be filled in. */
public record FieldRule(boolean collect, boolean required) {

    public FieldRule {
        if (required && !collect) {
            required = false;
        }
    }
}
