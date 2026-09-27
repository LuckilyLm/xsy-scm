package com.xsy.scm.order.service;

import com.xsy.scm.common.domain.entity.ScmIdempotencyRecordEntity;
import com.xsy.scm.common.idempotency.ScmIdempotencyService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** Compatibility facade for order commands; shared mechanics live in common. */
@Service
@RequiredArgsConstructor
public class OrderIdempotencyService {

    private final ScmIdempotencyService idempotencyService;

    public record Claim(ScmIdempotencyRecordEntity record, boolean replay) {
    }

    public Claim claim(String operationScope, String idempotencyKey, Object request) {
        ScmIdempotencyService.Claim claim = idempotencyService.claim(operationScope, idempotencyKey, request);
        return new Claim(claim.record(), claim.replay());
    }

    public <T> T replay(Claim claim, Class<
            T> resultType) {
        return idempotencyService.replay(toSharedClaim(claim), resultType);
    }

    public void complete(Claim claim, String resourceType, Long resourceId, Object result) {
        idempotencyService.complete(toSharedClaim(claim), resourceType, resourceId, result);
    }

    private static ScmIdempotencyService.Claim toSharedClaim(Claim claim) {
        return new ScmIdempotencyService.Claim(claim.record(), claim.replay());
    }
}
