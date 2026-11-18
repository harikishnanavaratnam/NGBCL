package lk.ngbcl.tournament.domain;

import java.time.Instant;

/** State of a registration window at a given moment. */
public enum WindowState {
    NOT_CONFIGURED,
    NOT_OPEN,
    OPEN,
    CLOSED;

    public static WindowState of(Instant opens, Instant closes, Instant now) {
        if (opens == null || closes == null) {
            return NOT_CONFIGURED;
        }
        if (now.isBefore(opens)) {
            return NOT_OPEN;
        }
        return now.isBefore(closes) ? OPEN : CLOSED;
    }
}
