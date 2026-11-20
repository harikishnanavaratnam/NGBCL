-- M7: standings rules (STA-001..004). Tables are computed from confirmed tie results on read;
-- only the ranking rules are stored.
CREATE TABLE standings_settings (
    tournament_id  uuid         PRIMARY KEY REFERENCES tournaments (id),
    points_win     integer      NOT NULL DEFAULT 2 CHECK (points_win BETWEEN 0 AND 10),
    points_draw    integer      NOT NULL DEFAULT 1 CHECK (points_draw BETWEEN 0 AND 10),
    points_loss    integer      NOT NULL DEFAULT 0 CHECK (points_loss BETWEEN 0 AND 10),
    tiebreakers    varchar(200) NOT NULL DEFAULT 'MATCHES_WON,GAME_DIFF,POINT_DIFF,HEAD_TO_HEAD',
    updated_at     timestamptz  NOT NULL,
    version        bigint       NOT NULL DEFAULT 0,
    CONSTRAINT standings_points_order CHECK (points_win >= points_draw AND points_draw >= points_loss)
);
