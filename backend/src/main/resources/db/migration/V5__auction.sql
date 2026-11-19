-- M4: player auction (AUC-001..035, ROS-001..006).
-- PostgreSQL is the single source of truth for live auction state (no in-memory state).

-- Per-player base price override; NULL = auction default base price.
ALTER TABLE tournament_players ADD COLUMN base_price bigint CHECK (base_price IS NULL OR base_price > 0);

CREATE TABLE auction_settings (
    tournament_id            uuid         PRIMARY KEY REFERENCES tournaments (id),
    initial_budget           bigint       NOT NULL DEFAULT 1000000 CHECK (initial_budget > 0),
    max_squad_size           integer      NOT NULL DEFAULT 8 CHECK (max_squad_size BETWEEN 1 AND 40),
    min_squad_size           integer      CHECK (min_squad_size IS NULL OR min_squad_size >= 1),
    default_base_price       bigint       NOT NULL DEFAULT 10000 CHECK (default_base_price > 0),
    min_increment            bigint       NOT NULL DEFAULT 5000 CHECK (min_increment > 0),
    allow_reauction          boolean      NOT NULL DEFAULT true,
    allow_manual_adjustment  boolean      NOT NULL DEFAULT true,
    starts_at                timestamptz,
    updated_at               timestamptz  NOT NULL,
    version                  bigint       NOT NULL DEFAULT 0,
    CONSTRAINT auction_settings_min_le_max CHECK (min_squad_size IS NULL OR min_squad_size <= max_squad_size)
);

CREATE TABLE auctions (
    id                 uuid         PRIMARY KEY,
    tournament_id      uuid         NOT NULL REFERENCES tournaments (id),
    status             varchar(20)  NOT NULL DEFAULT 'NOT_STARTED'
                       CHECK (status IN ('NOT_STARTED', 'LIVE', 'PAUSED', 'COMPLETED')),
    current_round      integer      NOT NULL DEFAULT 1 CHECK (current_round >= 1),
    current_lot_id     uuid,
    lots_opened        integer      NOT NULL DEFAULT 0,
    state_version      bigint       NOT NULL DEFAULT 0,
    settings_snapshot  jsonb,
    last_event         varchar(40),
    started_at         timestamptz,
    ended_at           timestamptz,
    last_action_at     timestamptz,
    created_at         timestamptz  NOT NULL,
    updated_at         timestamptz  NOT NULL,
    version            bigint       NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX auctions_tournament_uq ON auctions (tournament_id);

CREATE TABLE team_budgets (
    id              uuid         PRIMARY KEY,
    auction_id      uuid         NOT NULL REFERENCES auctions (id),
    team_id         uuid         NOT NULL REFERENCES teams (id),
    initial_budget  bigint       NOT NULL CHECK (initial_budget > 0),
    adjustments     bigint       NOT NULL DEFAULT 0,
    spent           bigint       NOT NULL DEFAULT 0 CHECK (spent >= 0),
    remaining       bigint       NOT NULL CHECK (remaining >= 0),
    squad_count     integer      NOT NULL DEFAULT 0 CHECK (squad_count >= 0),
    created_at      timestamptz  NOT NULL,
    updated_at      timestamptz  NOT NULL,
    version         bigint       NOT NULL DEFAULT 0,
    CONSTRAINT team_budgets_balance CHECK (spent + remaining = initial_budget + adjustments)
);
CREATE UNIQUE INDEX team_budgets_uq ON team_budgets (auction_id, team_id);

CREATE TABLE auction_lots (
    id                    uuid         PRIMARY KEY,
    auction_id            uuid         NOT NULL REFERENCES auctions (id),
    tournament_player_id  uuid         NOT NULL REFERENCES tournament_players (id),
    base_price            bigint       NOT NULL CHECK (base_price > 0),
    state                 varchar(20)  NOT NULL DEFAULT 'QUEUED'
                          CHECK (state IN ('QUEUED', 'OPEN', 'SOLD', 'UNSOLD', 'WITHDRAWN')),
    call_state            varchar(20)  NOT NULL DEFAULT 'NONE' CHECK (call_state IN ('NONE', 'GOING_ONCE', 'GOING_TWICE')),
    round                 integer      NOT NULL DEFAULT 1 CHECK (round >= 1),
    queue_order           integer      NOT NULL,
    lot_number            integer,
    current_bid_id        uuid,
    current_amount        bigint,
    leading_team_id       uuid         REFERENCES teams (id),
    bid_count             integer      NOT NULL DEFAULT 0,
    final_price           bigint,
    winning_team_id       uuid         REFERENCES teams (id),
    opened_at             timestamptz,
    closed_at             timestamptz,
    created_at            timestamptz  NOT NULL,
    updated_at            timestamptz  NOT NULL,
    version               bigint       NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX auction_lots_player_uq ON auction_lots (auction_id, tournament_player_id);
CREATE UNIQUE INDEX auction_lots_one_open_uq ON auction_lots (auction_id) WHERE state = 'OPEN';
CREATE INDEX auction_lots_queue_idx ON auction_lots (auction_id, round, state, queue_order);

-- Append-only bid history (AUC-034). Undo voids a bid; nothing is deleted.
CREATE TABLE auction_bids (
    id               uuid         PRIMARY KEY,
    lot_id           uuid         NOT NULL REFERENCES auction_lots (id),
    round            integer      NOT NULL,
    team_id          uuid         NOT NULL REFERENCES teams (id),
    amount           bigint       NOT NULL CHECK (amount > 0),
    bid_number       integer      NOT NULL,
    status           varchar(20)  NOT NULL DEFAULT 'ACCEPTED' CHECK (status IN ('ACCEPTED', 'VOIDED')),
    placed_by        uuid         REFERENCES users (id),
    idempotency_key  varchar(80)  NOT NULL,
    created_at       timestamptz  NOT NULL,
    voided_at        timestamptz,
    voided_by        uuid         REFERENCES users (id)
);
CREATE UNIQUE INDEX auction_bids_idem_uq ON auction_bids (idempotency_key);
CREATE INDEX auction_bids_lot_idx ON auction_bids (lot_id, bid_number);

CREATE TABLE auction_transactions (
    id                    uuid         PRIMARY KEY,
    auction_id            uuid         NOT NULL REFERENCES auctions (id),
    lot_id                uuid         NOT NULL REFERENCES auction_lots (id),
    team_id               uuid         NOT NULL REFERENCES teams (id),
    tournament_player_id  uuid         NOT NULL REFERENCES tournament_players (id),
    amount                bigint       NOT NULL CHECK (amount > 0),
    status                varchar(20)  NOT NULL DEFAULT 'SOLD' CHECK (status IN ('SOLD', 'REVERSED')),
    confirmed_by          uuid         REFERENCES users (id),
    confirmed_at          timestamptz  NOT NULL,
    reversed_by           uuid         REFERENCES users (id),
    reversed_at           timestamptz,
    reverse_reason        varchar(300)
);
CREATE UNIQUE INDEX auction_transactions_one_sold_uq ON auction_transactions (lot_id) WHERE status = 'SOLD';

-- Rosters (ROS-001..006): a player is active in at most one team per edition.
CREATE TABLE team_players (
    id                    uuid         PRIMARY KEY,
    tournament_id         uuid         NOT NULL REFERENCES tournaments (id),
    team_id               uuid         NOT NULL REFERENCES teams (id),
    tournament_player_id  uuid         NOT NULL REFERENCES tournament_players (id),
    source                varchar(20)  NOT NULL CHECK (source IN ('AUCTION', 'ADMIN')),
    purchase_price        bigint,
    transaction_id        uuid         REFERENCES auction_transactions (id),
    lot_number            integer,
    active                boolean      NOT NULL DEFAULT true,
    released_at           timestamptz,
    released_reason       varchar(300),
    created_at            timestamptz  NOT NULL,
    updated_at            timestamptz  NOT NULL,
    version               bigint       NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX team_players_active_uq ON team_players (tournament_id, tournament_player_id) WHERE active;
CREATE INDEX team_players_team_idx ON team_players (team_id, active);

CREATE TABLE budget_adjustments (
    id          uuid          PRIMARY KEY,
    budget_id   uuid          NOT NULL REFERENCES team_budgets (id),
    amount      bigint        NOT NULL CHECK (amount <> 0),
    reason      varchar(300)  NOT NULL,
    actor_id    uuid          REFERENCES users (id),
    created_at  timestamptz   NOT NULL
);
