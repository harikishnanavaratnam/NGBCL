-- M2: public registrations, review workflow, players, teams.

-- ---------------------------------------------------------------------------
-- Reference numbers: PLY-2026-00142, TEM-2026-0007 (PLY-007, TEM-005)
-- ---------------------------------------------------------------------------
CREATE TABLE reference_sequences (
    tournament_id  uuid        NOT NULL REFERENCES tournaments (id),
    kind           varchar(10) NOT NULL,
    next_value     integer     NOT NULL DEFAULT 1 CHECK (next_value > 0),
    PRIMARY KEY (tournament_id, kind)
);

-- ---------------------------------------------------------------------------
-- Anonymous registration sessions: scope uploads made before an applicant
-- submits (SRS §7.2 "short-lived registration upload token"). Only a hash of
-- the token is stored. Turnstile is checked when the form is submitted.
-- ---------------------------------------------------------------------------
CREATE TABLE registration_sessions (
    id          uuid        PRIMARY KEY,
    kind        varchar(10) NOT NULL CHECK (kind IN ('PLAYER', 'TEAM')),
    token_hash  varchar(64) NOT NULL,
    ip_hash     varchar(64),
    uploads     integer     NOT NULL DEFAULT 0,
    created_at  timestamptz NOT NULL,
    expires_at  timestamptz NOT NULL,
    used_at     timestamptz
);
CREATE UNIQUE INDEX registration_sessions_token_uq ON registration_sessions (token_hash);
CREATE INDEX registration_sessions_ip_idx ON registration_sessions (ip_hash, created_at);

ALTER TABLE file_assets ADD COLUMN upload_session_id uuid REFERENCES registration_sessions (id);

-- ---------------------------------------------------------------------------
-- Players: reusable profile across editions (SRS §6.2). Created or linked on approval.
-- ---------------------------------------------------------------------------
CREATE TABLE players (
    id                      uuid         PRIMARY KEY,
    full_name               varchar(120) NOT NULL,
    date_of_birth           date,
    gender                  varchar(20),
    email                   varchar(254) NOT NULL,
    phone                   varchar(30)  NOT NULL,
    address                 varchar(300),
    home_area               varchar(80),
    profile_photo_asset_id  uuid         REFERENCES file_assets (id),
    slug                    varchar(140) NOT NULL,
    created_at              timestamptz  NOT NULL,
    updated_at              timestamptz  NOT NULL,
    version                 bigint       NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX players_slug_uq ON players (slug);
CREATE INDEX players_email_idx ON players (lower(email));
CREATE INDEX players_phone_idx ON players (phone);

-- ---------------------------------------------------------------------------
-- Player registrations (SRS §6.3). Typed columns, not a JSON blob.
-- ---------------------------------------------------------------------------
CREATE TABLE player_registrations (
    id                   uuid         PRIMARY KEY,
    tournament_id        uuid         NOT NULL REFERENCES tournaments (id),
    registration_number  varchar(20)  NOT NULL,
    player_id            uuid         REFERENCES players (id),
    linked_player_id     uuid         REFERENCES players (id),
    full_name            varchar(120) NOT NULL,
    date_of_birth        date,
    gender               varchar(20),
    email                varchar(254) NOT NULL,
    phone                varchar(30)  NOT NULL,
    address              varchar(300),
    home_area            varchar(80),
    experience           varchar(300),
    emergency_name       varchar(120),
    emergency_phone      varchar(30),
    skill_category_id    uuid         REFERENCES player_categories (id),
    photo_asset_id       uuid         NOT NULL REFERENCES file_assets (id),
    jaffna_declared      boolean      NOT NULL CHECK (jaffna_declared),
    consent_at           timestamptz  NOT NULL,
    consent_version      varchar(20)  NOT NULL,
    status               varchar(20)  NOT NULL DEFAULT 'PENDING'
                         CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED', 'WITHDRAWN')),
    duplicate_flag       boolean      NOT NULL DEFAULT false,
    submitted_at         timestamptz  NOT NULL,
    reviewed_by          uuid         REFERENCES users (id),
    reviewed_at          timestamptz,
    rejection_reason     varchar(120),
    rejection_message    varchar(500),
    ip_hash              varchar(64),
    created_at           timestamptz  NOT NULL,
    updated_at           timestamptz  NOT NULL,
    version              bigint       NOT NULL DEFAULT 0,
    CONSTRAINT player_reg_rejection_ck CHECK (status <> 'REJECTED' OR rejection_reason IS NOT NULL)
);
CREATE UNIQUE INDEX player_reg_number_uq ON player_registrations (registration_number);
CREATE INDEX player_reg_queue_idx ON player_registrations (tournament_id, status, submitted_at);
CREATE INDEX player_reg_email_idx ON player_registrations (tournament_id, lower(email));
CREATE INDEX player_reg_phone_idx ON player_registrations (tournament_id, phone);

-- Possible duplicates (PLY-010/011): never merged automatically.
CREATE TABLE registration_duplicates (
    id                          uuid         PRIMARY KEY,
    registration_id             uuid         NOT NULL REFERENCES player_registrations (id),
    candidate_registration_id   uuid         REFERENCES player_registrations (id),
    candidate_player_id         uuid         REFERENCES players (id),
    matched_on                  varchar(60)  NOT NULL,
    resolution                  varchar(20)  NOT NULL DEFAULT 'OPEN'
                                CHECK (resolution IN ('OPEN', 'LINKED', 'NEW_PROFILE', 'NOT_DUPLICATE')),
    resolved_by                 uuid         REFERENCES users (id),
    resolved_at                 timestamptz,
    created_at                  timestamptz  NOT NULL,
    CONSTRAINT reg_dup_candidate_ck CHECK (candidate_registration_id IS NOT NULL OR candidate_player_id IS NOT NULL)
);
CREATE INDEX registration_duplicates_reg_idx ON registration_duplicates (registration_id);

-- Players in a specific edition (approved registrations). Auction states arrive in M4.
CREATE TABLE tournament_players (
    id                  uuid        PRIMARY KEY,
    tournament_id       uuid        NOT NULL REFERENCES tournaments (id),
    player_id           uuid        NOT NULL REFERENCES players (id),
    registration_id     uuid        NOT NULL REFERENCES player_registrations (id),
    skill_category_id   uuid        REFERENCES player_categories (id),
    status              varchar(20) NOT NULL DEFAULT 'APPROVED'
                        CHECK (status IN ('APPROVED', 'WITHDRAWN', 'INACTIVE')),
    status_reason       varchar(300),
    created_at          timestamptz NOT NULL,
    updated_at          timestamptz NOT NULL,
    version             bigint      NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX tournament_players_uq ON tournament_players (tournament_id, player_id);

-- ---------------------------------------------------------------------------
-- Teams (TEM-001..013)
-- ---------------------------------------------------------------------------
CREATE TABLE team_registrations (
    id                    uuid         PRIMARY KEY,
    tournament_id         uuid         NOT NULL REFERENCES tournaments (id),
    registration_number   varchar(20)  NOT NULL,
    team_name             varchar(60)  NOT NULL,
    description           varchar(300),
    logo_asset_id         uuid         NOT NULL REFERENCES file_assets (id),
    contact_name          varchar(120) NOT NULL,
    contact_email         varchar(254) NOT NULL,
    contact_phone         varchar(30)  NOT NULL,
    authorised_declared   boolean      NOT NULL CHECK (authorised_declared),
    fee_acknowledged      boolean      NOT NULL CHECK (fee_acknowledged),
    consent_at            timestamptz  NOT NULL,
    consent_version       varchar(20)  NOT NULL,
    status                varchar(20)  NOT NULL DEFAULT 'PENDING'
                          CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED', 'WITHDRAWN')),
    team_id               uuid,
    submitted_at          timestamptz  NOT NULL,
    reviewed_by           uuid         REFERENCES users (id),
    reviewed_at           timestamptz,
    rejection_reason      varchar(120),
    rejection_message     varchar(500),
    ip_hash               varchar(64),
    created_at            timestamptz  NOT NULL,
    updated_at            timestamptz  NOT NULL,
    version               bigint       NOT NULL DEFAULT 0,
    CONSTRAINT team_reg_rejection_ck CHECK (status <> 'REJECTED' OR rejection_reason IS NOT NULL)
);
CREATE UNIQUE INDEX team_reg_number_uq ON team_registrations (registration_number);
CREATE INDEX team_reg_queue_idx ON team_registrations (tournament_id, status, submitted_at);

CREATE TABLE teams (
    id                    uuid         PRIMARY KEY,
    tournament_id         uuid         NOT NULL REFERENCES tournaments (id),
    team_registration_id  uuid         REFERENCES team_registrations (id),
    name                  varchar(60)  NOT NULL,
    slug                  varchar(80)  NOT NULL,
    short_code            varchar(3)   NOT NULL CHECK (short_code ~ '^[A-Z0-9]{2,3}$'),
    color_slot            smallint     CHECK (color_slot IS NULL OR color_slot BETWEEN 1 AND 10),
    description           varchar(300),
    logo_asset_id         uuid         REFERENCES file_assets (id),
    contact_name          varchar(120),
    contact_email         varchar(254),
    contact_phone         varchar(30),
    creation_source       varchar(30)  NOT NULL CHECK (creation_source IN ('PUBLIC_REGISTRATION', 'ADMIN')),
    status                varchar(20)  NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'INACTIVE')),
    fee_received          boolean      NOT NULL DEFAULT false,
    fee_received_at       timestamptz,
    fee_received_by       uuid         REFERENCES users (id),
    created_by            uuid         REFERENCES users (id),
    created_at            timestamptz  NOT NULL,
    updated_at            timestamptz  NOT NULL,
    version               bigint       NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX teams_name_uq ON teams (tournament_id, lower(name));
CREATE UNIQUE INDEX teams_slug_uq ON teams (tournament_id, slug);
CREATE UNIQUE INDEX teams_code_uq ON teams (tournament_id, short_code);
CREATE UNIQUE INDEX teams_color_uq ON teams (tournament_id, color_slot) WHERE color_slot IS NOT NULL;

ALTER TABLE team_registrations ADD CONSTRAINT team_reg_team_fk FOREIGN KEY (team_id) REFERENCES teams (id);

-- ---------------------------------------------------------------------------
-- Internal notes on registrations (PLY-022). Never exposed publicly.
-- ---------------------------------------------------------------------------
CREATE TABLE registration_notes (
    id                 uuid          PRIMARY KEY,
    registration_kind  varchar(10)   NOT NULL CHECK (registration_kind IN ('PLAYER', 'TEAM')),
    registration_id    uuid          NOT NULL,
    author_id          uuid          NOT NULL REFERENCES users (id),
    author_name        varchar(120)  NOT NULL,
    body               varchar(1000) NOT NULL,
    created_at         timestamptz   NOT NULL
);
CREATE INDEX registration_notes_reg_idx ON registration_notes (registration_kind, registration_id, created_at);
