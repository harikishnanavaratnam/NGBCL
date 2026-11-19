package lk.ngbcl.auction.domain;

/** Auctioneer's "going once / going twice" call. Cosmetic: any new bid resets it. */
public enum CallState {
    NONE,
    GOING_ONCE,
    GOING_TWICE
}
