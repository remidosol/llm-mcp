package com.remidosol.llmmcp.credit.api;

import com.remidosol.llmmcp.credit.api.dto.TopUpRequest;
import com.remidosol.llmmcp.credit.application.CreditQueryService;
import com.remidosol.llmmcp.credit.application.CreditView;
import com.remidosol.llmmcp.credit.application.TopUpService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Thin inbound adapter. The top-up endpoint is admin-only from Phase 5 on (API key
 * filter); until then it is open in the local profile by design.
 */
@RestController
@RequestMapping("/api/credits")
class CreditController {

    private final CreditQueryService query;
    private final TopUpService topUp;

    CreditController(CreditQueryService query, TopUpService topUp) {
        this.query = query;
        this.topUp = topUp;
    }

    @GetMapping("/{userId}")
    CreditView get(@PathVariable String userId) {
        return query.getCredits(userId);
    }

    @PostMapping("/{userId}/topup")
    CreditView topUp(@PathVariable String userId, @Valid @RequestBody TopUpRequest request) {
        return topUp.topUp(userId, request.amount());
    }
}
