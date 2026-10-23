package com.xsy.scm.finance.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import com.xsy.scm.common.json.JsonbObjectMapTypeHandler;

import java.time.OffsetDateTime;
import java.util.Map;

/**
 * 一次财务写操作追加一条不可改日志。日志不继承财务单据字段，记录原单、动作、操作人及前后数据，不允许软删或更新覆盖历史。
 */
@Data
@TableName(value = "xsy_finance_operation_log", autoResultMap = true)
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
     * {@code ScmFinanceOperationTypeEnum}；取值由 {@code ck_finance_operation_log_type} 在数据库层限定。
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
     * 改前数据：生成类动作为 {@code null}；核销与反向类为目标当时的余额数据。
     */
    @TableField(typeHandler = JsonbObjectMapTypeHandler.class)
    private Map<String, Object> beforeData;

    /**
     * 改后数据：生成类为单头数据；核销与反向类为写入后的余额数据。
     */
    @TableField(typeHandler = JsonbObjectMapTypeHandler.class)
    private Map<String, Object> afterData;

    private OffsetDateTime createdAt;

    private String createdBy;
}
