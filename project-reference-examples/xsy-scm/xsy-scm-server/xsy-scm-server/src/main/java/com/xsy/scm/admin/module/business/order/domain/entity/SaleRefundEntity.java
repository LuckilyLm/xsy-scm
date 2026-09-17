package com.xsy.scm.admin.module.business.order.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 销售退款单 实体类
 *
 * @author xsy-scm
 */
@Data
@TableName("t_refund")
public class SaleRefundEntity {

    /**
     * 主键
     */
    @TableId(type = IdType.AUTO)
    private Long refundId;

    /**
     * 退款单号，TKD + yyyyMMdd + 4 位流水
     */
    private String refundNo;

    /**
     * 订单 ID
     */
    private Long orderId;

    /**
     * 订单明细 ID，整单退款时为空
     */
    private Long itemId;

    /**
     * 退款类型：1 仅退款，2 退货退款
     */
    private Integer refundType;

    /**
     * 退款金额（不含税）
     */
    private BigDecimal refundAmount;

    /**
     * 退款原因
     */
    private String refundReason;

    /**
     * 状态：1 待审核，2 已通过，3 已退款，4 已驳回
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
     * 删除状态：0 否，1 是（退款记录不允许物理删除）
     */
    private Boolean deletedFlag;
}
