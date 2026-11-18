-- M3: public website content (announcements, sponsors, committee) and public player text.

-- Playing experience is shown on the public player page (latest approved registration wins).
ALTER TABLE players ADD COLUMN experience varchar(300);

-- ---------------------------------------------------------------------------
-- Announcements / news (CNT-001..004)
-- Scheduled = PUBLISHED with publish_at in the future.
-- ---------------------------------------------------------------------------
CREATE TABLE announcements (
    id              uuid          PRIMARY KEY,
    tournament_id   uuid          NOT NULL REFERENCES tournaments (id),
    title           varchar(160)  NOT NULL,
    slug            varchar(180)  NOT NULL,
    body            text          NOT NULL CHECK (length(body) <= 20000),
    cover_asset_id  uuid          REFERENCES file_assets (id),
    status          varchar(20)   NOT NULL DEFAULT 'DRAFT' CHECK (status IN ('DRAFT', 'PUBLISHED', 'ARCHIVED')),
    pinned          boolean       NOT NULL DEFAULT false,
    publish_at      timestamptz,
    author_id       uuid          REFERENCES users (id),
    author_name     varchar(120),
    created_at      timestamptz   NOT NULL,
    updated_at      timestamptz   NOT NULL,
    version         bigint        NOT NULL DEFAULT 0,
    CONSTRAINT announcements_published_has_time CHECK (status <> 'PUBLISHED' OR publish_at IS NOT NULL)
);
CREATE UNIQUE INDEX announcements_slug_uq ON announcements (tournament_id, slug);
CREATE INDEX announcements_public_idx ON announcements (tournament_id, status, pinned, publish_at);

-- ---------------------------------------------------------------------------
-- Sponsors (CNT-005..007) — one active title sponsor per edition.
-- ---------------------------------------------------------------------------
CREATE TABLE sponsors (
    id              uuid          PRIMARY KEY,
    tournament_id   uuid          NOT NULL REFERENCES tournaments (id),
    name            varchar(120)  NOT NULL,
    tier            varchar(20)   NOT NULL CHECK (tier IN ('TITLE', 'PLATINUM', 'GOLD', 'SILVER', 'BRONZE', 'MEDIA')),
    logo_asset_id   uuid          REFERENCES file_assets (id),
    website_url     varchar(300),
    award_name      varchar(160),
    show_on_wall    boolean       NOT NULL DEFAULT true,
    show_on_ticker  boolean       NOT NULL DEFAULT true,
    active          boolean       NOT NULL DEFAULT true,
    display_order   integer       NOT NULL DEFAULT 0,
    created_at      timestamptz   NOT NULL,
    updated_at      timestamptz   NOT NULL,
    version         bigint        NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX sponsors_one_title_uq ON sponsors (tournament_id) WHERE tier = 'TITLE' AND active;
CREATE INDEX sponsors_tournament_idx ON sponsors (tournament_id, tier, display_order);

-- ---------------------------------------------------------------------------
-- Organising committee (CNT-008) — no personal contact details stored.
-- ---------------------------------------------------------------------------
CREATE TABLE committee_groups (
    id              uuid          PRIMARY KEY,
    tournament_id   uuid          NOT NULL REFERENCES tournaments (id),
    name            varchar(80)   NOT NULL,
    style           varchar(20)   NOT NULL DEFAULT 'COMPACT' CHECK (style IN ('LEAD', 'COMPACT')),
    display_order   integer       NOT NULL DEFAULT 0,
    created_at      timestamptz   NOT NULL,
    updated_at      timestamptz   NOT NULL,
    version         bigint        NOT NULL DEFAULT 0
);
CREATE INDEX committee_groups_tournament_idx ON committee_groups (tournament_id, display_order);

CREATE TABLE committee_members (
    id              uuid          PRIMARY KEY,
    group_id        uuid          NOT NULL REFERENCES committee_groups (id),
    full_name       varchar(120)  NOT NULL,
    role            varchar(120)  NOT NULL,
    bio             varchar(200),
    photo_asset_id  uuid          REFERENCES file_assets (id),
    visible         boolean       NOT NULL DEFAULT true,
    display_order   integer       NOT NULL DEFAULT 0,
    created_at      timestamptz   NOT NULL,
    updated_at      timestamptz   NOT NULL,
    version         bigint        NOT NULL DEFAULT 0
);
CREATE INDEX committee_members_group_idx ON committee_members (group_id, display_order);
