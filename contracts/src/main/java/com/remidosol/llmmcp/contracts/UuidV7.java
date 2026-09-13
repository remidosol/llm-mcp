package com.remidosol.llmmcp.contracts;

import java.security.SecureRandom;
import java.util.UUID;

/**
 * RFC 9562 UUIDv7: 48-bit Unix millisecond timestamp, version nibble 7, 74 random bits. Exists
 * because event ids double as inbox primary keys and outbox ordering keys — a time-ordered id keeps
 * B-tree inserts append-only and makes "sort by id" mean "sort by time" (PRD §4.3).
 */
public final class UuidV7 {

    private static final SecureRandom RANDOM = new SecureRandom();

    private UuidV7() {
    }

    public static UUID generate() {
        long millis = System.currentTimeMillis();
        long randA = RANDOM.nextLong() & 0x0FFFL; // 12 bits of sub-millisecond entropy
        long msb = (millis << 16) | 0x7000L | randA;
        long lsb = (RANDOM.nextLong() & 0x3FFFFFFFFFFFFFFFL) | 0x8000000000000000L; // variant 10xx
        return new UUID(msb, lsb);
    }

    /** The millisecond timestamp embedded in a v7 id (useful for latency metrics and debugging). */
    public static long timestampMillis(UUID uuid) {
        return uuid.getMostSignificantBits() >>> 16;
    }
}
