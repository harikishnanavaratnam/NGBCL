package lk.ngbcl.competition.domain;

/** FIX-002 formats. */
public enum CompetitionFormat {
    /** Groups play round robin; the top teams of each group go into a knockout bracket. */
    GROUPS_KNOCKOUT,
    /** One league, everyone plays everyone once; the table decides. */
    ROUND_ROBIN,
    /** One league, then a final between the top two (and optionally 3rd v 4th). */
    ROUND_ROBIN_FINAL,
    /** Single elimination from the start. */
    KNOCKOUT
}
