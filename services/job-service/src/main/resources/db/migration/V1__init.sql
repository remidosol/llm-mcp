-- job_db schema. Migrations are immutable: never edit, always add a new version.

create table job (
    id                uuid primary key,
    user_id           text        not null,
    prompt            text        not null,
    model             text        not null,
    status            text        not null,
    estimated_credits int         not null,
    actual_credits    int,
    failure_reason    text,
    created_at        timestamptz not null default now(),
    updated_at        timestamptz not null default now(),
    version           bigint      not null default 0
);
create index idx_job_status_updated_at on job (status, updated_at);
create index idx_job_user_id on job (user_id);

create table job_result (
    job_id            uuid primary key references job (id),
    output            text        not null,
    provider          text        not null,
    model             text        not null,
    prompt_tokens     int,
    completion_tokens int,
    late              boolean     not null default false,
    created_at        timestamptz not null default now()
);

-- Column names follow the Debezium outbox EventRouter defaults (id, aggregatetype,
-- aggregateid, type, payload) so the CDC connector needs minimal configuration.
create table outbox (
    id            uuid primary key, -- envelope eventId
    aggregatetype text        not null,
    aggregateid   text        not null,
    type          text        not null,
    payload       jsonb       not null,
    created_at    timestamptz not null default now(),
    published_at  timestamptz
);
create index idx_outbox_pending on outbox (published_at) where published_at is null;

-- Inbox for idempotent consumers: insert in the same transaction as the handler.
create table processed_event (
    event_id       uuid        not null,
    consumer_group text        not null,
    processed_at   timestamptz not null default now(),
    primary key (event_id, consumer_group)
);
