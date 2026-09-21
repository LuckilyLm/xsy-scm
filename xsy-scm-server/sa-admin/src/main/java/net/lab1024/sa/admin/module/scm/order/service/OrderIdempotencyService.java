package net.lab1024.sa.admin.module.scm.order.service;

import net.lab1024.sa.admin.module.scm.order.domain.entity.*;
import net.lab1024.sa.admin.module.scm.order.domain.form.*;
import net.lab1024.sa.admin.module.scm.common.exception.ScmBusinessException;

import static net.lab1024.sa.admin.module.scm.order.constant.OrderErrorCode.*;

import java.math.BigDecimal;
import java.util.*;

import net.lab1024.sa.admin.module.scm.order.dao.IdempotencyRecordDao;
import net.lab1024.sa.admin.module.scm.order.manager.OrderValidator;
import net.lab1024.sa.admin.module.scm.order.support.OrderIdempotencyRequestHasher;
import net.lab1024.sa.admin.module.scm.common.constant.ScmOperator;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class OrderIdempotencyService {
    private final IdempotencyRecordDao dao;
    private final ObjectMapper json;

    public record Claim(IdempotencyRecordEntity record, boolean replay) {
    }

    public Claim claim(String scope, String key, Object request) {
        OrderValidator.reason(key, ORDER_IDEMPOTENCY_KEY_REQUIRED);
        key = key.trim();
        if (key.length() > 200) throw new ScmBusinessException(ORDER_IDEMPOTENCY_KEY_INVALID);
        // Scope by authenticated actor as well as command: unrelated operators cannot replay each other's data.
        scope = ScmOperator.current() + ":" + scope;
        var hash = new OrderIdempotencyRequestHasher(json).hash(request);
        var row = new IdempotencyRecordEntity();
        row.setOperationScope(scope);
        row.setIdempotencyKey(key);
        row.setRequestHash(hash);
        row.setCreatedBy(ScmOperator.current());
        if (dao.claim(row) == 1) return new Claim(dao.find(scope, key), false);
        row = dao.find(scope, key);
        if (!Objects.equals(hash, row.getRequestHash())) throw new ScmBusinessException(ORDER_IDEMPOTENCY_CONFLICT);
        if (row.getResultData() == null) throw new IllegalStateException("Incomplete committed idempotency claim");
        return new Claim(row, true);
    }

    public <T> T replay(Claim claim, Class<T> type) {
        return json.convertValue(claim.record().getResultData().get("value"), type);
    }

    public void complete(Claim claim, String type, Long id, Object result) {
        var row = claim.record();
        row.setResultId(id);
        row.setResultType(type);
        var value = new LinkedHashMap<String, Object>();
        value.put("value", json.convertValue(result, Object.class));
        row.setResultData(value);
        row.setUpdatedAt(java.time.OffsetDateTime.now());
        row.setUpdatedBy(ScmOperator.current());
        if (dao.updateById(row) != 1) throw new IllegalStateException("Idempotency completion lost");
    }
}
