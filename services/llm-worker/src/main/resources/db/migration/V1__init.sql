-- llm_db schema

create table llm_attempt (
    id                uuid primary key,
    job_id            uuid        not null,
    provider          text        not null,
    model             text        not null,
    attempt_no        int         not null,
    status            text        not null,
    error             text,
    prompt_tokens     int,
    completion_tokens int,
    started_at        timestamptz not null default now(),
    finished_at       timestamptz
);
create index idx_llm_attempt_job_id on llm_attempt (job_id);

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
