package com.xsy.scm.admin.module.business.finance.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 发票 实体类
 *
 * <p>支持按税率拆分开票的关联红冲（relateInvoiceNos 记录同一订单的多张发票）。</p>
 *
 * @author xsy-scm
 */
@Data
@TableName("t_invoice")
public class InvoiceEntity {

    /**
     * 主键
     */
    @TableId(type = IdType.AUTO)
    private Long invoiceId;

    /**
     * 发票号
     */
    private String invoiceNo;

    /**
     * 关联订单 ID
     */
    private Long orderId;

    /**
     * 关联发票号（多张时逗号分隔，用于整单红冲联动）
     */
    private String relateInvoiceNos;

    /**
     * 价税合计
     */
    private BigDecimal amount;

    /**
     * 税率
     */
    private BigDecimal taxRate;

    /**
     * 状态：1 可开票，2 已开票，3 已红冲
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
