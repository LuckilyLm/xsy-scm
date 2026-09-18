package net.lab1024.sa.admin.module.scm.purchase.service;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.RequiredArgsConstructor;
import net.lab1024.sa.admin.module.scm.common.constant.ScmOperator;
import net.lab1024.sa.admin.module.scm.common.exception.ScmBusinessException;
import net.lab1024.sa.admin.module.scm.common.json.ScmOffsetDateTimeDeserializer;
import net.lab1024.sa.admin.module.scm.order.dao.IdempotencyRecordDao;
import net.lab1024.sa.admin.module.scm.order.domain.entity.IdempotencyRecordEntity;
import net.lab1024.sa.admin.module.scm.purchase.manager.PurchaseOrderValidator;
import net.lab1024.sa.admin.module.scm.purchase.support.PurchaseIdempotencyRequestHasher;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import static net.lab1024.sa.admin.module.scm.purchase.constant.PurchaseErrorCode.PURCHASE_IDEMPOTENCY_CONFLICT;
import static net.lab1024.sa.admin.module.scm.purchase.constant.PurchaseErrorCode.PURCHASE_IDEMPOTENCY_KEY_INVALID;
import static net.lab1024.sa.admin.module.scm.purchase.constant.PurchaseErrorCode.PURCHASE_IDEMPOTENCY_KEY_REQUIRED;

/**
 * 采购写命令的幂等控制，复用共享表 {@code idempotency_record}。
 * {@code claim} 通过唯一索引竞争执行权；调用方须将认领、业务写入和结果保存置于同一事务。
 */
@Service
@RequiredArgsConstructor
public class PurchaseIdempotencyService {

    private static final int MAX_KEY_LENGTH = 200;

    private final IdempotencyRecordDao idempotencyRecordDao;

    private final ObjectMapper json;

    /**
     * 结果存储专用 mapper：写入完整时间精度，并兼容读取旧的秒级展示格式。
     * 请求哈希仍使用 {@link #json}，避免改变既有幂等键的内容判定。
     */
    static final ObjectMapper RESULT_JSON = JsonMapper.builder()
            .addModule(new JavaTimeModule())
            // 覆盖 JavaTimeModule 的时间读取器，同时接受展示格式和 ISO-8601。
            .addModule(new SimpleModule()
                    .addDeserializer(OffsetDateTime.class, new ScmOffsetDateTimeDeserializer()))
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();

    /**
     * @param record 已提交（或本次新建）的幂等记录
     * @param replay {@code true} 表示命中已有记录，调用方必须直接返回
     *               {@link #replay(Claim, Class)} 的结果，不得再次执行副作用
     */
    public record Claim(IdempotencyRecordEntity record, boolean replay) {
    }

    /**
     * 认领幂等键。
     *
     * @throws ScmBusinessException 键缺失 → 40084；超长 → 40085；同键异内容 → 40990
     */
    public Claim claim(String scope, String key, Object request) {
        PurchaseOrderValidator.reason(key, PURCHASE_IDEMPOTENCY_KEY_REQUIRED);
        key = key.trim();
        if (key.length() > MAX_KEY_LENGTH) {
            throw new ScmBusinessException(PURCHASE_IDEMPOTENCY_KEY_INVALID);
        }
        String operator = ScmOperator.current();
        // 按操作者隔离幂等键，防止跨用户重放结果。
        scope = operator + ":" + scope;
        String hash = new PurchaseIdempotencyRequestHasher(json).hash(request);

        IdempotencyRecordEntity row = new IdempotencyRecordEntity();
        row.setOperationScope(scope);
        row.setIdempotencyKey(key);
        row.setRequestHash(hash);
        row.setCreatedBy(operator);

        if (idempotencyRecordDao.claim(row) == 1) {
            return new Claim(idempotencyRecordDao.find(scope, key), false);
        }

        row = idempotencyRecordDao.find(scope, key);
        if (!Objects.equals(hash, row.getRequestHash())) {
            throw new ScmBusinessException(PURCHASE_IDEMPOTENCY_CONFLICT);
        }
        if (row.getResultData() == null) {
            // 已提交记录缺少结果属于完整性异常，不能当作可重试的新请求。
            throw new IllegalStateException("已提交的幂等记录缺少 result_data（数据完整性异常）");
        }
        return new Claim(row, true);
    }

    /** 返回首次执行的结果，不重新执行业务写入。 */
    public <T> T replay(Claim claim, Class<T> type) {
        return RESULT_JSON.convertValue(claim.record().getResultData().get("value"), type);
    }

    /** 保存结果，与调用方的业务写入一起提交或回滚。 */
    public void complete(Claim claim, String type, Long id, Object result) {
        IdempotencyRecordEntity row = claim.record();
        row.setResultId(id);
        row.setResultType(type);
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("value", RESULT_JSON.convertValue(result, Object.class));
        row.setResultData(value);
        row.setUpdatedAt(OffsetDateTime.now());
        row.setUpdatedBy(ScmOperator.current());
        if (idempotencyRecordDao.updateById(row) != 1) {
            throw new IllegalStateException("幂等结果写入失败（影响行数不为 1）");
        }
    }
}
