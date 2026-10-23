package com.xsy.scm.order.service;

import com.xsy.scm.common.domain.entity.ScmIdempotencyRecordEntity;
import com.xsy.scm.common.idempotency.ScmIdempotencyService;

import java.util.Optional;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 订单命令的兼容封装；通用幂等逻辑在 common 层。
 */
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

    /**
     * 只读预检：存在同键、请求内容一致且已完成的记录时返回它，否则返回空。
     *
     * <p>
     * 供「先看是否已经成功」的入口使用，语义见 {@link ScmIdempotencyService#peekCompleted}。
     */
    public Optional<Claim> peekCompleted(String operationScope, String idempotencyKey, Object request) {
        return idempotencyService.peekCompleted(operationScope, idempotencyKey, request)
            .map(claim -> new Claim(claim.record(), claim.replay()));
    }

    public <T> T replay(Claim claim, Class<T> resultType) {
        return idempotencyService.replay(toSharedClaim(claim), resultType);
    }

    public void complete(Claim claim, String resourceType, Long resourceId, Object result) {
        idempotencyService.complete(toSharedClaim(claim), resourceType, resourceId, result);
    }

    private static ScmIdempotencyService.Claim toSharedClaim(Claim claim) {
        return new ScmIdempotencyService.Claim(claim.record(), claim.replay());
    }
}
