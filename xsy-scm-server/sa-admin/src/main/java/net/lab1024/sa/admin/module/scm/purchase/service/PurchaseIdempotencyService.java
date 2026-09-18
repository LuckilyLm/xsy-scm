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
 * 采购写命令的幂等（W5 Target Design §7.11）。
 *
 * <p><b>复用 W4 的 `idempotency_record` 表</b>（不新建表、不修改 W4 代码），但遵守三条纪律：
 * <ol>
 *   <li><b>scope 拼操作者</b>（修 A-D14）：`scope = ScmOperator.current() + ":" + scope`。
 *       不同操作者用同一个 `Idempotency-Key` 也不会互相重放；
 *       隔离由 `uk_idempotency_record_scope_key_active` 保证（键含 scope）；</li>
 *   <li><b>claim 用 INSERT 竞争</b>（修 A-D17）：先 INSERT，靠唯一索引判胜负，
 *       **不先查后插**。并发下只有一个事务能拿到 claim，其余走重放路径；</li>
 *   <li><b>complete 与业务写入同一事务</b>：`result_data` 存 {@code {"value": ...}}，
 *       这样重放返回的是**首次的真实结果**，而不是重新执行一次。</li>
 * </ol>
 *
 * <p><b>为什么 `result_data == null` 要抛 `IllegalStateException` 而不是业务错误</b>：
 * claim 与 complete 在同一事务内，能读到「已提交但没有结果」的行说明数据被外部改过 ——
 * 这是程序/数据完整性问题，不是用户输入问题，不应伪装成 4xxxx 业务码。
 *
 * <p><b>为什么复用 W4 的 Dao/Entity 而不复制</b>：`idempotency_record` 是**跨域共享的
 * 基础设施表**（不是订单业务表），V13 建表时已按通用形状设计（`operation_scope` 是自由文本）。
 * 复制一份 Entity 会让同一个表出现两套映射，反而增加不一致风险。
 * 这与 `PurchaseJsonbTypeHandler` 刻意不复用 `OrderJsonbTypeHandler` 并不矛盾 ——
 * 后者是**领域内的序列化细节**，前者是**跨域共享表**。
 */
@Service
@RequiredArgsConstructor
public class PurchaseIdempotencyService {

    /** 幂等键长度上限（§7.1 统一约定）。 */
    private static final int MAX_KEY_LENGTH = 200;

    private final IdempotencyRecordDao idempotencyRecordDao;

    private final ObjectMapper json;

    /**
     * 幂等结果的**存储专用** mapper —— 与注入的 {@link #json}（Spring 的 ObjectMapper）刻意分开。
     *
     * <p><b>为什么不能复用 {@code json}</b>：Spring 的 mapper 上注册了
     * {@code ScmOffsetDateTimeSerializer}，它把 {@code OffsetDateTime} 输出成**展示用**的
     * {@code yyyy-MM-dd HH:mm:ss}（秒级、丢精度）。用它写幂等结果会让
     * 「重放返回首次的**真实**结果」这条契约失真：时间字段被截断到秒，
     * 重放值与首次值不再相等（{@code PurchaseOrderIdempotencyIT} 断言的正是这个相等）。
     *
     * <p><b>根因是「一个 mapper 干了两件事」</b>：展示格式是**对外**契约（要好看、秒级足够），
     * 幂等结果是**对内**持久化（要无损、可精确还原）。两者必须分开。
     *
     * <p><b>读入侧刻意同时接受展示格式</b>（{@link ScmOffsetDateTimeDeserializer}）：
     * 在只注册了序列化器的那段时间里，首次执行成功的请求已经写下了一批**展示格式**的结果
     * （失败发生在重放而不是首次执行），那些行必须仍然可以被重放 ——
     * 否则客户端拿着一个旧幂等键重试会永久失败。
     *
     * <p><b>刻意不动 {@code json} 在 {@code claim} 里的用法</b>：请求哈希由
     * {@code PurchaseIdempotencyRequestHasher(json)} 计算，换 mapper 会让同一份请求算出
     * 不同的哈希，从而把「同键同内容重试」误判成 40990（同键异内容）。
     * 哈希口径属于已冻结的对外行为，不在本次修正范围内。
     */
    static final ObjectMapper RESULT_JSON = JsonMapper.builder()
            .addModule(new JavaTimeModule())
            // 注册在 JavaTimeModule **之后**：后注册的同类型反序列化器覆盖先前的，
            // 这样展示格式与 ISO-8601 都能读。
            .addModule(new SimpleModule()
                    .addDeserializer(OffsetDateTime.class, new ScmOffsetDateTimeDeserializer()))
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();

    /**
     * @param record 已提交（或本次新建）的幂等记录
     * @param replay {@code true} = 命中已有记录，调用方必须**直接返回**
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
        // scope 前缀拼操作者：不同操作者不可互相重放（修 A-D14）
        scope = operator + ":" + scope;
        String hash = new PurchaseIdempotencyRequestHasher(json).hash(request);

        IdempotencyRecordEntity row = new IdempotencyRecordEntity();
        row.setOperationScope(scope);
        row.setIdempotencyKey(key);
        row.setRequestHash(hash);
        row.setCreatedBy(operator);

        if (idempotencyRecordDao.claim(row) == 1) {
            // INSERT 竞争胜出：本次是首次执行
            return new Claim(idempotencyRecordDao.find(scope, key), false);
        }

        row = idempotencyRecordDao.find(scope, key);
        if (!Objects.equals(hash, row.getRequestHash())) {
            // 同键异内容：拒绝，而不是「按首次结果返回」—— 后者会掩盖调用方的 bug
            throw new ScmBusinessException(PURCHASE_IDEMPOTENCY_CONFLICT);
        }
        if (row.getResultData() == null) {
            throw new IllegalStateException("已提交的幂等记录缺少 result_data（数据完整性异常）");
        }
        return new Claim(row, true);
    }

    /**
     * 取回首次执行的结果。
     *
     * <p>用 {@link #RESULT_JSON}（无损）而不是注入的 {@code json}（展示格式）——
     * 见 {@link #RESULT_JSON} 的注释。
     */
    public <T> T replay(Claim claim, Class<T> type) {
        return RESULT_JSON.convertValue(claim.record().getResultData().get("value"), type);
    }

    /** 在**同一事务内**写入结果（与业务写入一起提交或一起回滚）。 */
    public void complete(Claim claim, String type, Long id, Object result) {
        IdempotencyRecordEntity row = claim.record();
        row.setResultId(id);
        row.setResultType(type);
        Map<String, Object> value = new LinkedHashMap<>();
        // 写与读必须用同一个 mapper，且必须是**无损**的那个（见 RESULT_JSON 的注释）
        value.put("value", RESULT_JSON.convertValue(result, Object.class));
        row.setResultData(value);
        row.setUpdatedAt(OffsetDateTime.now());
        row.setUpdatedBy(ScmOperator.current());
        if (idempotencyRecordDao.updateById(row) != 1) {
            throw new IllegalStateException("幂等结果写入失败（影响行数不为 1）");
        }
    }
}
