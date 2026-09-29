package com.xsy.scm.common.idempotency;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xsy.scm.common.constant.ScmOperator;
import com.xsy.scm.common.dao.ScmIdempotencyRecordDao;
import com.xsy.scm.common.domain.entity.ScmIdempotencyRecordEntity;
import com.xsy.scm.common.error.ScmCommonErrorCode;
import com.xsy.scm.common.error.ScmErrorCode;
import com.xsy.scm.common.exception.ScmBusinessException;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Shared persistence and replay mechanics for idempotent SCM commands. */
@Service
@RequiredArgsConstructor
public class ScmIdempotencyService {

    private static final int MAX_KEY_LENGTH = 200;

    private final ScmIdempotencyRecordDao idempotencyRecordDao;
    private final ObjectMapper objectMapper;

    public record Claim(ScmIdempotencyRecordEntity record, boolean replay) {
    }

    public Claim claim(String scope, String key, Object request) {
        return claim(scope, key, request, ScmCommonErrorCode.IDEMPOTENCY_KEY_REQUIRED,
                ScmCommonErrorCode.IDEMPOTENCY_KEY_INVALID, ScmCommonErrorCode.IDEMPOTENCY_CONFLICT,
                "已提交的幂等记录缺少 result_data（数据完整性异常）");
    }

    public Claim claim(String scope, String key, Object request, ScmErrorCode missingKeyError,
            ScmErrorCode invalidKeyError, ScmErrorCode conflictError, String incompleteResultMessage) {
        if (StringUtils.isBlank(key))
            throw new ScmBusinessException(missingKeyError);
        String normalizedKey = key.trim();
        if (normalizedKey.length() > MAX_KEY_LENGTH)
            throw new ScmBusinessException(invalidKeyError);

        String operator = ScmOperator.current();
        String operationScope = operator + ":" + scope;
        String requestHash = new ScmIdempotencyRequestHasher(objectMapper).hash(request);
        var row = new ScmIdempotencyRecordEntity();
        row.setOperationScope(operationScope);
        row.setIdempotencyKey(normalizedKey);
        row.setRequestHash(requestHash);
        row.setCreatedBy(operator);

        if (idempotencyRecordDao.claim(row) == 1) {
            return new Claim(idempotencyRecordDao.find(operationScope, normalizedKey), false);
        }
        row = idempotencyRecordDao.find(operationScope, normalizedKey);
        if (!Objects.equals(requestHash, row.getRequestHash()))
            throw new ScmBusinessException(conflictError);
        if (row.getResultData() == null)
            throw new IllegalStateException(incompleteResultMessage);
        return new Claim(row, true);
    }

    public <T> T replay(Claim claim, Class<T> resultType) {
        return replay(claim, resultType, objectMapper);
    }

    public <T> T replay(Claim claim, Class<T> resultType, ObjectMapper resultMapper) {
        return resultMapper.convertValue(claim.record().getResultData().get("value"), resultType);
    }

    public void complete(Claim claim, String resourceType, Long resourceId, Object result) {
        complete(claim, resourceType, resourceId, result, objectMapper);
    }

    public void complete(Claim claim, String resourceType, Long resourceId, Object result, ObjectMapper resultMapper) {
        ScmIdempotencyRecordEntity row = claim.record();
        row.setResultId(resourceId);
        row.setResultType(resourceType);
        Map<String, Object> resultData = new LinkedHashMap<>();
        resultData.put("value", resultMapper.convertValue(result, Object.class));
        row.setResultData(resultData);
        row.setUpdatedAt(OffsetDateTime.now());
        row.setUpdatedBy(ScmOperator.current());
        if (idempotencyRecordDao.updateById(row) != 1) {
            throw new IllegalStateException("幂等结果写入失败（影响行数不为 1）");
        }
    }
}
