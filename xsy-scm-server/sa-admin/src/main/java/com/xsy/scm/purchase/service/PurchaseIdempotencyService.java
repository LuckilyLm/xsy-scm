package com.xsy.scm.purchase.service;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.RequiredArgsConstructor;
import com.xsy.scm.common.domain.entity.ScmIdempotencyRecordEntity;
import com.xsy.scm.common.idempotency.ScmIdempotencyService;
import com.xsy.scm.common.json.ScmOffsetDateTimeDeserializer;
import com.xsy.scm.purchase.constant.PurchaseErrorCode;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;

/**
 * 采购写命令的幂等控制，复用共享表 {@code idempotency_record}。
 * {@code claim} 通过唯一索引竞争执行权；调用方须将认领、业务写入和结果保存置于同一事务。
 */
@Service
@RequiredArgsConstructor
public class PurchaseIdempotencyService {

    private final ScmIdempotencyService idempotencyService;

    /**
     * 结果存储专用 mapper：写入完整时间精度，并兼容读取旧的秒级展示格式。
     * 该 mapper 只负责结果回放；请求哈希由公共幂等服务保持统一规则。
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
    public record Claim(ScmIdempotencyRecordEntity record, boolean replay) {
    }

    /**
     * 认领幂等键。
     *
     * @throws ScmBusinessException 键缺失 → 40084；超长 → 40085；同键异内容 → 40990
     */
    public Claim claim(String scope, String key, Object request) {
        ScmIdempotencyService.Claim sharedClaim = idempotencyService.claim(
                scope,
                key,
                request,
                PurchaseErrorCode.PURCHASE_IDEMPOTENCY_KEY_REQUIRED,
                PurchaseErrorCode.PURCHASE_IDEMPOTENCY_KEY_INVALID,
                PurchaseErrorCode.PURCHASE_IDEMPOTENCY_CONFLICT,
                "已提交的幂等记录缺少 result_data（数据完整性异常）");
        return new Claim(sharedClaim.record(), sharedClaim.replay());
    }

    /**
     * 返回首次执行的结果，不重新执行业务写入。
     */
    public <T> T replay(Claim claim, Class<T> resultType) {
        return idempotencyService.replay(
                new ScmIdempotencyService.Claim(claim.record(), claim.replay()), resultType, RESULT_JSON);
    }

    /**
     * 保存结果，与调用方的业务写入一起提交或回滚。
     */
    public void complete(Claim claim, String resourceType, Long resourceId, Object result) {
        idempotencyService.complete(
                new ScmIdempotencyService.Claim(claim.record(), claim.replay()),
                resourceType, resourceId, result, RESULT_JSON);
    }
}
