-- Loaded only when the 'local' profile adds classpath:db/local to spring.flyway.locations.
-- Live example of per-profile Flyway locations.
-- Credit account seeds (u1/u2) belong to credit_db (credit-service has its own seed file).

insert into job (id, user_id, prompt, model, status, estimated_credits)
values ('00000000-0000-0000-0000-000000000001', 'u1', 'Seed: say hello', 'fake:demo', 'CREATED', 4)
on conflict (id) do nothing;
