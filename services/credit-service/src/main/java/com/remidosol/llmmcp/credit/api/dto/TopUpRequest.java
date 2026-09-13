package com.remidosol.llmmcp.credit.api.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/** Body of {@code POST /api/credits/{userId}/topup}. */
public record TopUpRequest(@Min(1) @Max(1_000_000) long amount) {
}
