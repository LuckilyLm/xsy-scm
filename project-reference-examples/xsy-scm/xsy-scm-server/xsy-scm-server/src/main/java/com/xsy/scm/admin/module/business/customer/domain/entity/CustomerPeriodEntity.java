package com.xsy.scm.admin.module.business.customer.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 客户账期 实体类
 *
 * @author xsy-scm
 */
@Data
@TableName("t_customer_period")
public class CustomerPeriodEntity {

    /**
     * 主键
     */
    @TableId(type = IdType.AUTO)
    private Long periodId;

    /**
     * 客户 ID
     */
    private Long customerId;

    /**
     * 账期类型：1 按金额，2 按时间
     */
    private Integer periodType;

    /**
     * 金额阈值（不含税），按金额时使用
     */
    private BigDecimal amountThreshold;

    /**
     * 账期值，按时间时使用
     */
    private Integer periodValue;

    /**
     * 账期单位：1 天，2 月
     */
    private Integer periodUnit;

    /**
     * 固定结算日，按月时使用
     */
    private Integer settleDay;

    /**
     * 状态：1 生效，2 暂停
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
