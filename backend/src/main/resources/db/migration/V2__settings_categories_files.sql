-- M1: tournament settings, skill categories, file assets (Cloudflare R2).

-- ---------------------------------------------------------------------------
-- More public tournament details (General settings tab)
-- ---------------------------------------------------------------------------
ALTER TABLE tournaments
    ADD COLUMN tagline        varchar(160),
    ADD COLUMN organiser      varchar(160),
    ADD COLUMN presenter_line varchar(160),
    ADD COLUMN courts         smallint CHECK (courts IS NULL OR courts BETWEEN 1 AND 20);

UPDATE tournaments
   SET tagline = 'Rise. Rally. Rule the Court.',
       organiser = 'Mighty Sharks Battalion',
       presenter_line = 'Mighty Sharks Battalion presents'
 WHERE slug = 'ngbcl-1-0';

-- ---------------------------------------------------------------------------
-- File assets (SRS §6.3 file_assets, §7). Binaries live in R2; only metadata here.
-- Every upload lands in the PRIVATE bucket; a re-encoded copy goes to the PUBLIC
-- bucket only after the asset is verified (BR-020).
-- ---------------------------------------------------------------------------
CREATE TABLE file_assets (
    id                   uuid         PRIMARY KEY,
    tournament_id        uuid         NOT NULL REFERENCES tournaments (id),
    purpose              varchar(30)  NOT NULL,
    original_filename    varchar(255),
    declared_type        varchar(100) NOT NULL,
    declared_size        bigint       NOT NULL CHECK (declared_size > 0),
    content_type         varchar(100),
    file_extension       varchar(10),
    file_size            bigint       CHECK (file_size IS NULL OR file_size > 0),
    width                integer,
    height               integer,
    checksum             varchar(64),
    object_key           varchar(300) NOT NULL,
    upload_status        varchar(20)  NOT NULL DEFAULT 'PENDING'
                         CHECK (upload_status IN ('PENDING', 'UPLOADED', 'INVALID', 'DELETED')),
    invalid_reason       varchar(255),
    verification_status  varchar(20)  NOT NULL DEFAULT 'UNVERIFIED'
                         CHECK (verification_status IN ('UNVERIFIED', 'VERIFIED', 'REJECTED')),
    verified_by          uuid         REFERENCES users (id),
    verified_at          timestamptz,
    rejection_reason     varchar(255),
    public_object_key    varchar(300),
    published_at         timestamptz,
    uploaded_by          uuid         REFERENCES users (id),
    deleted_at           timestamptz,
    created_at           timestamptz  NOT NULL,
    updated_at           timestamptz  NOT NULL,
    version              bigint       NOT NULL DEFAULT 0,
    CONSTRAINT file_assets_publish_needs_verify CHECK (public_object_key IS NULL OR verification_status = 'VERIFIED')
);
CREATE UNIQUE INDEX file_assets_object_key_uq ON file_assets (object_key);
CREATE INDEX file_assets_pending_idx ON file_assets (created_at) WHERE upload_status = 'PENDING';
CREATE INDEX file_assets_tournament_idx ON file_assets (tournament_id, purpose);

-- ---------------------------------------------------------------------------
-- Tournament settings (TRN-003, TRN-005). One row per tournament.
-- ---------------------------------------------------------------------------
CREATE TABLE tournament_settings (
    tournament_id            uuid        PRIMARY KEY REFERENCES tournaments (id),

    -- Registration windows
    player_reg_opens_at      timestamptz,
    player_reg_closes_at     timestamptz,
    team_reg_opens_at        timestamptz,
    team_reg_closes_at       timestamptz,

    -- Player form
    category_required        boolean     NOT NULL DEFAULT false,
    player_fields            jsonb       NOT NULL,
    dup_match_email          boolean     NOT NULL DEFAULT true,
    dup_match_phone          boolean     NOT NULL DEFAULT true,
    dup_match_name_dob       boolean     NOT NULL DEFAULT false,
    max_photo_bytes          integer     NOT NULL DEFAULT 5242880 CHECK (max_photo_bytes BETWEEN 262144 AND 20971520),
    min_photo_width          integer     NOT NULL DEFAULT 600 CHECK (min_photo_width BETWEEN 100 AND 4000),
    min_photo_height         integer     NOT NULL DEFAULT 750 CHECK (min_photo_height BETWEEN 100 AND 5000),
    notify_on_registration   boolean     NOT NULL DEFAULT false,

    -- Public site
    phase_override           varchar(30) CHECK (phase_override IS NULL OR phase_override IN
                             ('PRE_LAUNCH', 'REGISTRATION', 'REGISTRATION_CLOSED', 'AUCTION', 'RUNNING', 'COMPLETED')),
    show_player_prices       boolean     NOT NULL DEFAULT true,
    show_team_budgets        boolean     NOT NULL DEFAULT false,
    facebook_url             varchar(300),
    contact_email            varchar(254),
    contact_phone            varchar(30),
    prize_first              bigint      CHECK (prize_first IS NULL OR prize_first >= 0),
    prize_second             bigint      CHECK (prize_second IS NULL OR prize_second >= 0),
    prize_third              bigint      CHECK (prize_third IS NULL OR prize_third >= 0),
    team_entry_fee           bigint      CHECK (team_entry_fee IS NULL OR team_entry_fee >= 0),

    -- Branding (verified + published file assets)
    logo_asset_id            uuid        REFERENCES file_assets (id),
    favicon_asset_id         uuid        REFERENCES file_assets (id),
    organiser_logo_asset_id  uuid        REFERENCES file_assets (id),
    poster_asset_id          uuid        REFERENCES file_assets (id),

    updated_at               timestamptz NOT NULL,
    version                  bigint      NOT NULL DEFAULT 0,

    CONSTRAINT settings_player_window_ck CHECK (player_reg_closes_at IS NULL OR player_reg_opens_at IS NULL
                                                OR player_reg_closes_at > player_reg_opens_at),
    CONSTRAINT settings_team_window_ck CHECK (team_reg_closes_at IS NULL OR team_reg_opens_at IS NULL
                                              OR team_reg_closes_at > team_reg_opens_at)
);

-- Defaults for NGBCL 1.0: prizes and entry fee from the launch poster / sponsorship proposal.
INSERT INTO tournament_settings (tournament_id, player_fields, facebook_url, prize_first, prize_second, prize_third,
                                 team_entry_fee, updated_at)
SELECT id,
       '{"dateOfBirth":{"collect":true,"required":true},"gender":{"collect":true,"required":true},"address":{"collect":true,"required":false},"homeArea":{"collect":true,"required":true},"experience":{"collect":true,"required":false},"emergencyContact":{"collect":true,"required":false}}'::jsonb,
       'https://www.facebook.com/share/1Ea6AVLhXd/?mibextid=wwXIfr',
       300000, 200000, 100000, 150000, now()
  FROM tournaments;

-- ---------------------------------------------------------------------------
-- Player skill categories (CAT-001..006). Never deleted — deactivated instead.
-- ---------------------------------------------------------------------------
CREATE TABLE player_categories (
    id             uuid         PRIMARY KEY,
    tournament_id  uuid         NOT NULL REFERENCES tournaments (id),
    name           varchar(60)  NOT NULL,
    description    varchar(300),
    is_active      boolean      NOT NULL DEFAULT true,
    display_order  integer      NOT NULL,
    created_at     timestamptz  NOT NULL,
    updated_at     timestamptz  NOT NULL,
    version        bigint       NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX player_categories_name_uq ON player_categories (tournament_id, lower(name));
CREATE INDEX player_categories_order_idx ON player_categories (tournament_id, display_order);
