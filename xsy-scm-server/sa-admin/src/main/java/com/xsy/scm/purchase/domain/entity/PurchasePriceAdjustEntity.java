package com.xsy.scm.purchase.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import lombok.Data;

/**
 * 采购价补录 / 调整审计，append-only。
 *
 * <p>
 * 操作日志的 JSONB 快照回答「谁改了什么」，本表回答「这条明细的价格变过几次、每次从多少到多少」。 供应商对账按这张表核价，因此不做更新与删除。
 */
@Data
@TableName("xsy_purchase_price_adjust")
public class PurchasePriceAdjustEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long purchaseOrderId;

    private Long purchaseOrderItemId;

    private Long skuId;

    /** 为空表示该行此前是待定价。 */
    private BigDecimal oldPrice;

    private BigDecimal newPrice;

    /** MANUAL 人工 / AUTO_LAST_PRICE 超窗口按最近已提交价回填。 */
    private String source;

    private String reason;

    private String operator;

    private String idempotencyKey;

    private OffsetDateTime createdAt;
}
