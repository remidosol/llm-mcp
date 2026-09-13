# Kafka Connect image with the Debezium Postgres plugin baked in (ADR-0015: prebuilt image instead of
# KafkaConnect.spec.build, which needs a registry to push to — kind has none). Built by `make kind-load`.
FROM quay.io/strimzi/kafka:1.2.0-kafka-4.3.1
USER root:root
ARG DEBEZIUM_VERSION=3.6.1.Final
RUN mkdir -p /opt/kafka/plugins/debezium-postgres \
 && curl -sSL "https://repo1.maven.org/maven2/io/debezium/debezium-connector-postgres/${DEBEZIUM_VERSION}/debezium-connector-postgres-${DEBEZIUM_VERSION}-plugin.tar.gz" \
    | tar -xz -C /opt/kafka/plugins/debezium-postgres --strip-components=1
USER 1001
