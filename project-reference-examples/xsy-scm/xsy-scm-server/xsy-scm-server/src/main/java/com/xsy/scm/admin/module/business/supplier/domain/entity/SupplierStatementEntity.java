package com.xsy.scm.admin.module.business.supplier.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 供应商对账单 实体类
 *
 * @author xsy-scm
 */
@Data
@TableName("t_supplier_statement")
public class SupplierStatementEntity {

    /**
     * 主键
     */
    @TableId(type = IdType.AUTO)
    private Long statementId;

    /**
     * 对账单号，DZD + yyyyMMdd + 4 位流水
     */
    private String statementNo;

    /**
     * 供应商 ID
     */
    private Long supplierId;

    /**
     * 对账周期开始
     */
    private LocalDate periodStart;

    /**
     * 对账周期结束
     */
    private LocalDate periodEnd;

    /**
     * 对账总金额（不含税）
     */
    private BigDecimal totalAmount;

    /**
     * 已结算金额（不含税）
     */
    private BigDecimal paidAmount;

    /**
     * 状态：1 待供应商确认，2 供应商已确认，3 已结算，4 已驳回
     */
    private Integer status;

    /**
     * 创建人ID
     */
    private Long createUserId;

    /**
     * 创建人姓名
     */
    private String createUserName;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;

    /**
     * 删除状态：0 否，1 是
     */
    private Boolean deletedFlag;
}
