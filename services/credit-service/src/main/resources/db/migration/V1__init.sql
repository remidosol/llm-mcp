-- credit_db schema (PRD §4.4). Migrations are immutable: never edit, always add a new version.

-- balance is what the user owns; reserved is the semantic lock held by in-flight jobs (Richardson,
-- saga countermeasures). Readers MUST interpret available = balance - reserved.
create table credit_account (
    user_id    text primary key,
    balance    bigint      not null default 0,
    reserved   bigint      not null default 0,
    version    bigint      not null default 0,
    updated_at timestamptz not null default now(),
    constraint chk_credit_reserved_non_negative check (reserved >= 0)
);

-- job_id is UNIQUE: reservation is idempotent even if the inbox check were bypassed (PRD §4.2).
create table credit_reservation (
    id              uuid primary key,
    job_id          uuid        not null unique,
    user_id         text        not null,
    amount          bigint      not null,
    captured_amount bigint,
    status          text        not null,
    created_at      timestamptz not null default now(),
    updated_at      timestamptz not null default now()
);
create index idx_credit_reservation_user_id on credit_reservation (user_id);

create table outbox (
    id            uuid primary key,
    aggregatetype text        not null,
    aggregateid   text        not null,
    type          text        not null,
    payload       jsonb       not null,
    created_at    timestamptz not null default now(),
    published_at  timestamptz
);
create index idx_outbox_pending on outbox (published_at) where published_at is null;

create table processed_event (
    event_id       uuid        not null,
    consumer_group text        not null,
    processed_at   timestamptz not null default now(),
    primary key (event_id, consumer_group)
);
