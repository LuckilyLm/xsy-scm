package net.lab1024.sa.admin.module.scm.finance.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import net.lab1024.sa.admin.module.scm.common.json.JsonbObjectMapTypeHandler;

import java.time.OffsetDateTime;
import java.util.Map;

/**
 * 财务操作日志：一次财务写动作一行，形态照 {@code OrderOperationLogEntity}。
 *
 * <p><b>不继承 {@link FinanceRecord}</b>：本表连 {@code deleted} 与 {@code version} 都没有 ——
 * append-only 是结构性的，不存在删除或修改入口，因此不需要一列恒为 {@code FALSE} 的
 * {@code deleted} 来表达它（对照 {@code order_operation_log}）。
 *
 * <p><b>不复用 {@code t_operate_log}</b>：通用日志不保证与业务事务同成同败，也不带金额快照与
 * 类型白名单（P1 裁决 12 的判据）。财务需要的是「改前 / 改后金额级证据」，
 * 因此 {@code beforeData} / {@code afterData} 是本表存在的理由。
 *
 * <p>唯一写入口是 {@code FinanceOperationLogRecorder}，<b>必须与业务写同一事务</b>，
 * 否则会出现「库已改、日志没落」。
 */
@Data
@TableName(value = "finance_operation_log", autoResultMap = true)
public class FinanceOperationLogEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * {@code ScmFinanceBusinessTypeEnum}。
     */
    private String businessType;

    /**
     * 对应财务事实主键。
     */
    private Long businessId;

    /**
     * {@code ScmFinanceOperationTypeEnum}；白名单由 {@code ck_finance_operation_log_type} 在库级强制。
     */
    private String operationType;

    /**
     * 操作人（loginId 形态）。
     */
    private String operator;

    /**
     * 原因；破坏性动作必填，由服务层与对应事实表的 CHECK 共同保证。
     */
    private String reason;

    /**
     * 改前快照：生成类动作为 {@code null}；核销与反向类为目标的派生余额快照。
     */
    @TableField(typeHandler = JsonbObjectMapTypeHandler.class)
    private Map<String, Object> beforeData;

    /**
     * 改后快照：生成类为单头快照；核销与反向类为写入后的派生余额快照。
     */
    @TableField(typeHandler = JsonbObjectMapTypeHandler.class)
    private Map<String, Object> afterData;

    private OffsetDateTime createdAt;

    private String createdBy;
}
