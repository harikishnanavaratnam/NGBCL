-- M6: scoring and results (SRS §4.10). Rally-by-rally score events, games, two-step approval,
-- corrections, tie results and knockout progression.

-- Edition defaults (Settings › Scoring rules). Each match may override them until it starts.
CREATE TABLE scoring_settings (
    tournament_id      uuid         PRIMARY KEY REFERENCES tournaments (id),
    points_to_win      integer      NOT NULL DEFAULT 21 CHECK (points_to_win BETWEEN 5 AND 50),
    win_by             integer      NOT NULL DEFAULT 2 CHECK (win_by BETWEEN 1 AND 5),
    point_cap          integer      NOT NULL DEFAULT 30 CHECK (point_cap BETWEEN 5 AND 99),
    games_to_win       integer      NOT NULL DEFAULT 2 CHECK (games_to_win BETWEEN 1 AND 3),
    allow_walkover     boolean      NOT NULL DEFAULT true,
    allow_retirement   boolean      NOT NULL DEFAULT true,
    two_step_approval  boolean      NOT NULL DEFAULT true,
    updated_at         timestamptz  NOT NULL,
    version            bigint       NOT NULL DEFAULT 0,
    CHECK (point_cap >= points_to_win)
);

-- Match: its own rules (copied from the defaults at start, or set by an organiser before),
-- result fields and the approval trail.
ALTER TABLE matches
    ADD COLUMN points_to_win     integer,
    ADD COLUMN win_by            integer,
    ADD COLUMN point_cap         integer,
    ADD COLUMN games_to_win      integer,
    ADD COLUMN rules_custom      boolean      NOT NULL DEFAULT false,
    ADD COLUMN first_server      varchar(1)   CHECK (first_server IN ('A', 'B')),
    ADD COLUMN outcome           varchar(10)  CHECK (outcome IN ('NORMAL', 'WALKOVER', 'RETIRED')),
    ADD COLUMN winner_side       varchar(1)   CHECK (winner_side IN ('A', 'B')),
    ADD COLUMN games_a           integer      NOT NULL DEFAULT 0,
    ADD COLUMN games_b           integer      NOT NULL DEFAULT 0,
    ADD COLUMN points_a          integer      NOT NULL DEFAULT 0,
    ADD COLUMN points_b          integer      NOT NULL DEFAULT 0,
    ADD COLUMN state_version     bigint       NOT NULL DEFAULT 0,
    ADD COLUMN started_at        timestamptz,
    ADD COLUMN started_by        uuid         REFERENCES users (id),
    ADD COLUMN submitted_at      timestamptz,
    ADD COLUMN submitted_by      uuid         REFERENCES users (id),
    ADD COLUMN confirmed_at      timestamptz,
    ADD COLUMN confirmed_by      uuid         REFERENCES users (id),
    ADD COLUMN returned_reason   varchar(300),
    ADD COLUMN outcome_note      varchar(300);

-- One row per game played (or in progress).
CREATE TABLE match_games (
    id           uuid         PRIMARY KEY,
    match_id     uuid         NOT NULL REFERENCES matches (id) ON DELETE CASCADE,
    game_number  integer      NOT NULL CHECK (game_number >= 1),
    score_a      integer      NOT NULL DEFAULT 0 CHECK (score_a >= 0),
    score_b      integer      NOT NULL DEFAULT 0 CHECK (score_b >= 0),
    winner_side  varchar(1)   CHECK (winner_side IN ('A', 'B')),
    completed_at timestamptz
);
CREATE UNIQUE INDEX match_games_number_uq ON match_games (match_id, game_number);

-- Append-only rally log (score history). client_event_id makes every tap idempotent.
CREATE TABLE score_events (
    id               uuid         PRIMARY KEY,
    match_id         uuid         NOT NULL REFERENCES matches (id) ON DELETE CASCADE,
    seq              integer      NOT NULL,
    client_event_id  varchar(80)  NOT NULL,
    type             varchar(16)  NOT NULL CHECK (type IN ('START', 'POINT', 'UNDO', 'MANUAL', 'WALKOVER',
                                                           'RETIRED', 'SUBMIT', 'CONFIRM', 'RETURN', 'CORRECTION')),
    side             varchar(1)   CHECK (side IN ('A', 'B')),
    game_number      integer,
    score_a          integer,
    score_b          integer,
    voided           boolean      NOT NULL DEFAULT false,
    note             varchar(300),
    recorded_by      uuid         REFERENCES users (id),
    recorded_at      timestamptz  NOT NULL
);
CREATE UNIQUE INDEX score_events_seq_uq ON score_events (match_id, seq);
CREATE UNIQUE INDEX score_events_client_uq ON score_events (match_id, client_event_id);

-- Tie result: matches won per side; a team-fixture result is final once every match is confirmed.
ALTER TABLE team_fixtures
    ADD COLUMN matches_won_a  integer     NOT NULL DEFAULT 0,
    ADD COLUMN matches_won_b  integer     NOT NULL DEFAULT 0,
    ADD COLUMN result         varchar(5)  CHECK (result IN ('A', 'B', 'DRAW'));
