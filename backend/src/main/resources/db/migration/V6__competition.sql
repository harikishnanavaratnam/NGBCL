-- M5: competition — match categories, fixture template, courts, format, groups, team fixtures,
-- individual matches and line-ups (SRS §4.9, FIX-001..010). Scores arrive in M6.

-- Match categories (FIX-007): code + players per side + who may play.
CREATE TABLE match_categories (
    id                uuid         PRIMARY KEY,
    tournament_id     uuid         NOT NULL REFERENCES tournaments (id),
    code              varchar(8)   NOT NULL CHECK (code ~ '^[A-Z0-9]{1,8}$'),
    name              varchar(60)  NOT NULL,
    players_per_side  smallint     NOT NULL CHECK (players_per_side IN (1, 2)),
    gender_rule       varchar(10)  NOT NULL DEFAULT 'ANY' CHECK (gender_rule IN ('ANY', 'MALE', 'FEMALE', 'MIXED')),
    display_order     integer      NOT NULL DEFAULT 0,
    active            boolean      NOT NULL DEFAULT true,
    created_at        timestamptz  NOT NULL,
    updated_at        timestamptz  NOT NULL,
    version           bigint       NOT NULL DEFAULT 0,
    CHECK (gender_rule <> 'MIXED' OR players_per_side = 2)
);
CREATE UNIQUE INDEX match_categories_code_uq ON match_categories (tournament_id, code);

-- The matches every tie contains, in playing order.
CREATE TABLE fixture_template_items (
    id             uuid     PRIMARY KEY,
    tournament_id  uuid     NOT NULL REFERENCES tournaments (id),
    position       integer  NOT NULL CHECK (position >= 1),
    category_id    uuid     NOT NULL REFERENCES match_categories (id)
);
CREATE UNIQUE INDEX fixture_template_pos_uq ON fixture_template_items (tournament_id, position);

CREATE TABLE courts (
    id             uuid         PRIMARY KEY,
    tournament_id  uuid         NOT NULL REFERENCES tournaments (id),
    name           varchar(40)  NOT NULL,
    display_order  integer      NOT NULL DEFAULT 0,
    active         boolean      NOT NULL DEFAULT true,
    created_at     timestamptz  NOT NULL,
    updated_at     timestamptz  NOT NULL,
    version        bigint       NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX courts_name_uq ON courts (tournament_id, lower(name));

-- Format and scheduling rules (FIX-001/002/009). One row per edition.
CREATE TABLE competition_settings (
    tournament_id              uuid         PRIMARY KEY REFERENCES tournaments (id),
    format                     varchar(24)  NOT NULL DEFAULT 'GROUPS_KNOCKOUT'
                               CHECK (format IN ('GROUPS_KNOCKOUT', 'ROUND_ROBIN', 'ROUND_ROBIN_FINAL', 'KNOCKOUT')),
    group_count                integer      NOT NULL DEFAULT 2 CHECK (group_count BETWEEN 1 AND 8),
    qualifiers_per_group       integer      NOT NULL DEFAULT 2 CHECK (qualifiers_per_group BETWEEN 1 AND 8),
    third_place_match          boolean      NOT NULL DEFAULT true,
    max_matches_per_player     integer      NOT NULL DEFAULT 2 CHECK (max_matches_per_player BETWEEN 1 AND 10),
    match_minutes              integer      NOT NULL DEFAULT 40 CHECK (match_minutes BETWEEN 5 AND 240),
    rest_minutes               integer      NOT NULL DEFAULT 0 CHECK (rest_minutes BETWEEN 0 AND 240),
    updated_at                 timestamptz  NOT NULL,
    version                    bigint       NOT NULL DEFAULT 0
);

CREATE TABLE stages (
    id             uuid         PRIMARY KEY,
    tournament_id  uuid         NOT NULL REFERENCES tournaments (id),
    type           varchar(12)  NOT NULL CHECK (type IN ('GROUP', 'LEAGUE', 'KNOCKOUT')),
    name           varchar(60)  NOT NULL,
    stage_order    integer      NOT NULL,
    created_at     timestamptz  NOT NULL
);
CREATE UNIQUE INDEX stages_order_uq ON stages (tournament_id, stage_order);

CREATE TABLE competition_groups (
    id             uuid         PRIMARY KEY,
    tournament_id  uuid         NOT NULL REFERENCES tournaments (id),
    name           varchar(20)  NOT NULL,
    group_order    integer      NOT NULL,
    created_at     timestamptz  NOT NULL
);
CREATE UNIQUE INDEX competition_groups_order_uq ON competition_groups (tournament_id, group_order);

CREATE TABLE group_teams (
    id        uuid     PRIMARY KEY,
    group_id  uuid     NOT NULL REFERENCES competition_groups (id) ON DELETE CASCADE,
    team_id   uuid     NOT NULL REFERENCES teams (id),
    seed      integer  NOT NULL
);
CREATE UNIQUE INDEX group_teams_team_uq ON group_teams (team_id);

-- A team fixture (tie): two teams, several matches (FIX-003..005). Knockout slots start empty and
-- say where their team comes from (e.g. "Group A winner", "Winner of SF1").
CREATE TABLE team_fixtures (
    id                    uuid         PRIMARY KEY,
    tournament_id         uuid         NOT NULL REFERENCES tournaments (id),
    stage_id              uuid         NOT NULL REFERENCES stages (id),
    group_id              uuid         REFERENCES competition_groups (id),
    fixture_number        integer      NOT NULL,
    round_number          integer      NOT NULL,
    round_name            varchar(40)  NOT NULL,
    bracket_position      integer,
    team_a_id             uuid         REFERENCES teams (id),
    team_b_id             uuid         REFERENCES teams (id),
    source_a              varchar(60),
    source_b              varchar(60),
    winner_to_fixture_id  uuid         REFERENCES team_fixtures (id),
    winner_to_side        varchar(1)   CHECK (winner_to_side IN ('A', 'B')),
    loser_to_fixture_id   uuid         REFERENCES team_fixtures (id),
    loser_to_side         varchar(1)   CHECK (loser_to_side IN ('A', 'B')),
    scheduled_at          timestamptz,
    court_id              uuid         REFERENCES courts (id),
    status                varchar(12)  NOT NULL DEFAULT 'DRAFT'
                          CHECK (status IN ('DRAFT', 'SCHEDULED', 'LIVE', 'COMPLETED', 'CANCELLED')),
    published             boolean      NOT NULL DEFAULT false,
    published_at          timestamptz,
    changed_since_publish boolean      NOT NULL DEFAULT false,
    published_snapshot    jsonb,
    winner_team_id        uuid         REFERENCES teams (id),
    created_at            timestamptz  NOT NULL,
    updated_at            timestamptz  NOT NULL,
    version               bigint       NOT NULL DEFAULT 0,
    CHECK (team_a_id IS NULL OR team_b_id IS NULL OR team_a_id <> team_b_id)
);
CREATE UNIQUE INDEX team_fixtures_number_uq ON team_fixtures (tournament_id, fixture_number);
CREATE INDEX team_fixtures_schedule_idx ON team_fixtures (tournament_id, scheduled_at);

-- Individual matches inside a tie (FIX-006). Scoring fields arrive in M6.
CREATE TABLE matches (
    id                uuid         PRIMARY KEY,
    fixture_id        uuid         NOT NULL REFERENCES team_fixtures (id) ON DELETE CASCADE,
    match_order       integer      NOT NULL CHECK (match_order >= 1),
    category_id       uuid         NOT NULL REFERENCES match_categories (id),
    players_per_side  smallint     NOT NULL CHECK (players_per_side IN (1, 2)),
    scheduled_at      timestamptz,
    court_id          uuid         REFERENCES courts (id),
    scorer_user_id    uuid         REFERENCES users (id),
    status            varchar(12)  NOT NULL DEFAULT 'SCHEDULED'
                      CHECK (status IN ('SCHEDULED', 'LIVE', 'SUBMITTED', 'COMPLETED', 'CANCELLED')),
    created_at        timestamptz  NOT NULL,
    updated_at        timestamptz  NOT NULL,
    version           bigint       NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX matches_order_uq ON matches (fixture_id, match_order);

-- Line-ups: who plays for each side (FIX-008 roster check is in the service; the DB stops a
-- player appearing twice in one match or a slot being filled twice).
CREATE TABLE match_players (
    id                    uuid      PRIMARY KEY,
    match_id              uuid      NOT NULL REFERENCES matches (id) ON DELETE CASCADE,
    side                  varchar(1) NOT NULL CHECK (side IN ('A', 'B')),
    position              smallint  NOT NULL CHECK (position IN (1, 2)),
    team_id               uuid      NOT NULL REFERENCES teams (id),
    tournament_player_id  uuid      NOT NULL REFERENCES tournament_players (id)
);
CREATE UNIQUE INDEX match_players_slot_uq ON match_players (match_id, side, position);
CREATE UNIQUE INDEX match_players_once_uq ON match_players (match_id, tournament_player_id);
CREATE INDEX match_players_player_idx ON match_players (tournament_player_id);
