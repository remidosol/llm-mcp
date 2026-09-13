-- Phase 7: the W3C trace context of the transaction that wrote the row, so the publisher (poller or
-- Debezium) can put it on the Kafka record and the consumer's span joins the SAME trace.
alter table outbox add column traceparent text;
