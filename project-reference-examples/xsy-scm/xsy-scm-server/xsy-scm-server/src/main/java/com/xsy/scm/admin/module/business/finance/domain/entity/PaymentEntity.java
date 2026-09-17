package com.xsy.scm.admin.module.business.finance.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 收款单 实体类
 *
 * <p>客户回款登记，确认后核销应收（09-01 已定）。</p>
 *
 * @author xsy-scm
 */
@Data
@TableName("t_payment")
public class PaymentEntity {

    /**
     * 主键
     */
    @TableId(type = IdType.AUTO)
    private Long paymentId;

    /**
     * 收款单号：SKD + yyyyMMdd + 流水（按日重置）
     */
    private String paymentNo;

    /**
     * 关联订单 ID
     */
    private Long orderId;

    /**
     * 核销目标应收单 ID
     */
    private Long receivableId;

    /**
     * 客户 ID
     */
    private Long customerId;

    /**
     * 收款金额（不含税）
     */
    private BigDecimal amount;

    /**
     * 收款渠道：1 现金，2 转账，3 在线支付，4 余额扣减
     */
    private Integer payChannel;

    /**
     * 收款时间
     */
    private LocalDateTime payTime;

    /**
     * 凭证图片
     */
    private String proofImage;

    /**
     * 状态：1 待确认，2 已确认，3 已驳回
     */
    private Integer status;

    /**
     * 备注
     */
    private String remark;

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
     * 删除状态：0 否，1 是（收款记录不允许物理删除）
     */
    private Boolean deletedFlag;
}
