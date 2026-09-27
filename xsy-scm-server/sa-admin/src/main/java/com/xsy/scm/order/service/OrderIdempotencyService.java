package com.xsy.scm.order.service;

import com.xsy.scm.order.domain.entity.IdempotencyRecordEntity;


import com.xsy.scm.common.exception.ScmBusinessException;

import static com.xsy.scm.order.constant.OrderErrorCode.ORDER_IDEMPOTENCY_CONFLICT;
import static com.xsy.scm.order.constant.OrderErrorCode.ORDER_IDEMPOTENCY_KEY_INVALID;
import static com.xsy.scm.order.constant.OrderErrorCode.ORDER_IDEMPOTENCY_KEY_REQUIRED;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Objects;

import com.xsy.scm.order.dao.IdempotencyRecordDao;
import com.xsy.scm.order.manager.OrderValidator;
import com.xsy.scm.order.support.OrderIdempotencyRequestHasher;
import com.xsy.scm.common.constant.ScmOperator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xsy.scm.common.error.ScmErrorCode;
import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class OrderIdempotencyService {
    private final IdempotencyRecordDao idempotencyRecordDao;
    private final ObjectMapper objectMapper;

    public record Claim(IdempotencyRecordEntity record, boolean replay) {
    }

    public Claim claim(String operationScope, String idempotencyKey, Object request) {
        return claim(operationScope, idempotencyKey, request, ORDER_IDEMPOTENCY_KEY_REQUIRED,
                ORDER_IDEMPOTENCY_KEY_INVALID, ORDER_IDEMPOTENCY_CONFLICT,
                "Incomplete committed idempotency claim");
    }

    public Claim claim(String operationScope, String idempotencyKey, Object request,
            ScmErrorCode missingKeyError, ScmErrorCode invalidKeyError, ScmErrorCode conflictError,
            String incompleteResultMessage) {
        OrderValidator.reason(idempotencyKey, missingKeyError);
        idempotencyKey = idempotencyKey.trim();
        if (idempotencyKey.length() > 200) throw new ScmBusinessException(invalidKeyError);
        // Scope by authenticated actor as well as command: unrelated operators cannot replay each other's data.
        operationScope = ScmOperator.current() + ":" + operationScope;
        var hash = new OrderIdempotencyRequestHasher(objectMapper).hash(request);
        var row = new IdempotencyRecordEntity();
        row.setOperationScope(operationScope);
        row.setIdempotencyKey(idempotencyKey);
        row.setRequestHash(hash);
        row.setCreatedBy(ScmOperator.current());
        if (idempotencyRecordDao.claim(row) == 1) {
            return new Claim(idempotencyRecordDao.find(operationScope, idempotencyKey), false);
        }
        row = idempotencyRecordDao.find(operationScope, idempotencyKey);
        if (!Objects.equals(hash, row.getRequestHash())) throw new ScmBusinessException(conflictError);
        if (row.getResultData() == null) throw new IllegalStateException(incompleteResultMessage);
        return new Claim(row, true);
    }

    public <T> T replay(Claim claim, Class<T> resultType) {
        return replay(claim.record(), resultType, objectMapper);
    }

    public void complete(Claim claim, String resourceType, Long resourceId, Object result) {
        complete(claim.record(), resourceType, resourceId, result, objectMapper);
    }

    public <T> T replay(IdempotencyRecordEntity record, Class<T> resultType, ObjectMapper resultMapper) {
        return resultMapper.convertValue(record.getResultData().get("value"), resultType);
    }

    public void complete(IdempotencyRecordEntity row, String resourceType, Long resourceId, Object result,
            ObjectMapper resultMapper) {
        row.setResultId(resourceId);
        row.setResultType(resourceType);
        var value = new LinkedHashMap<String, Object>();
        value.put("value", resultMapper.convertValue(result, Object.class));
        row.setResultData(value);
        row.setUpdatedAt(java.time.OffsetDateTime.now());
        row.setUpdatedBy(ScmOperator.current());
        if (idempotencyRecordDao.updateById(row) != 1) throw new IllegalStateException("Idempotency completion lost");
    }
}
