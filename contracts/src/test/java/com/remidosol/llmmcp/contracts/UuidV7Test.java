package com.remidosol.llmmcp.contracts;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class UuidV7Test {

    @Test
    void has_version_7_and_rfc_variant() {
        UUID id = UuidV7.generate();

        assertThat(id.version()).isEqualTo(7);
        assertThat(id.variant()).isEqualTo(2); // IETF RFC variant (10xx)
    }

    @Test
    void embeds_the_creation_time_and_is_time_ordered() {
        long before = System.currentTimeMillis();
        UUID first = UuidV7.generate();
        UUID second = UuidV7.generate();
        long after = System.currentTimeMillis();

        assertThat(UuidV7.timestampMillis(first)).isBetween(before, after);
        assertThat(UuidV7.timestampMillis(second)).isGreaterThanOrEqualTo(UuidV7.timestampMillis(first));
    }
}
