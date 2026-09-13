-- Local/demo accounts (PRD §4.4): u1 can afford jobs, u2 (5 credits) demonstrates CreditRejected.
insert into credit_account (user_id, balance, reserved)
values ('u1', 1000, 0),
       ('u2', 5, 0)
on conflict (user_id) do nothing;
