package com.xsy.scm.admin.module.business.purchase.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 供应商报价 实体类
 *
 * @author xsy-scm
 */
@Data
@TableName("t_inquiry_quote")
public class InquiryQuoteEntity {

    /**
     * 主键
     */
    @TableId(type = IdType.AUTO)
    private Long quoteId;

    /**
     * 询价单 ID
     */
    private Long inquiryId;

    /**
     * 询价明细 ID
     */
    private Long itemId;

    /**
     * 供应商 ID
     */
    private Long supplierId;

    /**
     * 报价（不含税）
     */
    private BigDecimal quotePrice;

    /**
     * 报价时间
     */
    private LocalDateTime quoteTime;

    /**
     * 供应商综合评分（权重加权，0~100）
     */
    private BigDecimal score;

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
