package net.lab1024.sa.admin.module.scm.finance.support;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import net.lab1024.sa.admin.module.scm.common.constant.ScmOperator;
import net.lab1024.sa.admin.module.scm.finance.constant.ScmFinanceBusinessTypeEnum;
import net.lab1024.sa.admin.module.scm.finance.constant.ScmFinanceOperationTypeEnum;
import net.lab1024.sa.admin.module.scm.finance.dao.FinanceOperationLogDao;
import net.lab1024.sa.admin.module.scm.finance.domain.entity.FinanceOperationLogEntity;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 财务操作日志的唯一写入口，形态照 {@code OrderOperationLogRecorder}。
 *
 * <p><b>调用方必须在自身事务内使用</b>：日志与财务事实变更必须同事务，
 * 否则会出现「账已改、日志没落」—— 对财务而言那等于证据链断裂。
 * 本类刻意不加 {@code @Transactional}，事务边界由调用方（写命令）决定。
 *
 * <p><b>不复用 {@code t_operate_log}</b>：通用日志不保证与业务事务同成同败，
 * 也不带金额快照与类型白名单（P1 裁决 12 的判据）。财务需要的是「改前 / 改后金额级证据」，
 * 因此 {@code before} / {@code after} 两个 JSONB 快照是本表存在的理由。
 *
 * <p>快照口径（设计稿 §17）：
 * <ul>
 *   <li>生成类（{@code GENERATE} / {@code RED_GENERATE}）：{@code before} 为 {@code null}，
 *       {@code after} 为单头快照；</li>
 *   <li>核销与反向核销：{@code before} 为目标的派生余额快照，{@code after} 为写入后的派生余额快照；</li>
 *   <li>收付款反向（D-3）：{@code before} 为原行有效额快照，{@code after} 为反向后快照。</li>
 * </ul>
 * 派生余额快照必须在**持有目标行锁之后**取，否则记下的是一份并发下已经不成立的数字。
 */
@Component
@RequiredArgsConstructor
public class FinanceOperationLogRecorder {

    private final FinanceOperationLogDao logs;
    private final ObjectMapper json;

    /**
     * 追加一条财务操作日志。
     *
     * @param businessType 财务对象类型，与 {@code businessId} 一起定位被操作的事实
     * @param businessId   财务事实主键（不存单号：单号是读时 join 取得的派生展示值）
     * @param operation    动作类型，取值受 {@code ck_finance_operation_log_type} 白名单约束
     * @param reason       原因；破坏性动作必填，由调用方与对应事实表的 CHECK 共同保证
     * @param before       改前快照，可为 {@code null}（生成类动作没有「改前」）
     * @param after        改后快照
     */
    public void record(ScmFinanceBusinessTypeEnum businessType, Long businessId,
                       ScmFinanceOperationTypeEnum operation, String reason,
                       Object before, Object after) {
        var entry = new FinanceOperationLogEntity();
        entry.setBusinessType(businessType.name());
        entry.setBusinessId(businessId);
        entry.setOperationType(operation.name());
        entry.setOperator(ScmOperator.current());
        entry.setCreatedBy(entry.getOperator());
        entry.setReason(reason);
        entry.setBeforeData(toJsonMap(before));
        entry.setAfterData(toJsonMap(after));
        logs.insert(entry);
    }

    private Map<String, Object> toJsonMap(Object value) {
        if (value == null) {
            return null;
        }
        return json.convertValue(value, new TypeReference<Map<String, Object>>() {
        });
    }
}
