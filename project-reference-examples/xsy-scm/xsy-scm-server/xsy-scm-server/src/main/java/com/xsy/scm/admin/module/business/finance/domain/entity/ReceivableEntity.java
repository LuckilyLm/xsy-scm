package com.xsy.scm.admin.module.business.finance.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 应收单 实体类
 *
 * <p>订单签收后生成（09-01 已定）。金额一律 DECIMAL，不含税。</p>
 *
 * @author xsy-scm
 */
@Data
@TableName("t_receivable")
public class ReceivableEntity {

    /**
     * 主键
     */
    @TableId(type = IdType.AUTO)
    private Long receivableId;

    /**
     * 应收单号：YSD + yyyyMMdd + 流水（按日重置）
     */
    private String receivableNo;

    /**
     * 订单 ID
     */
    private Long orderId;

    /**
     * 下单客户 ID
     */
    private Long customerId;

    /**
     * 结算客户 ID（集团统一结算时挂集团客户）
     */
    private Long settleCustomerId;

    /**
     * 结算方式：1 账期支付，2 货到付款，3 在线支付，4 余额充值
     */
    private Integer settleType;

    /**
     * 应收金额（不含税，取订单核算金额）
     */
    private BigDecimal amount;

    /**
     * 已收金额（不含税）
     */
    private BigDecimal receivedAmount;

    /**
     * 待收余额（不含税）
     */
    private BigDecimal balanceAmount;

    /**
     * 到期日（按客户账期计算）
     */
    private LocalDateTime dueTime;

    /**
     * 状态：1 待收款，2 部分收款，3 已结清，4 已冲销
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
     * 删除状态：0 否，1 是（应收不允许物理删除）
     */
    private Boolean deletedFlag;
}
