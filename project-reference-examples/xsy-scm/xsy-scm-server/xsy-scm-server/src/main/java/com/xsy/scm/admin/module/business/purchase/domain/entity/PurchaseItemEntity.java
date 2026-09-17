package com.xsy.scm.admin.module.business.purchase.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 采购明细 实体类
 *
 * @author xsy-scm
 */
@Data
@TableName("t_purchase_item")
public class PurchaseItemEntity {

    /**
     * 主键
     */
    @TableId(type = IdType.AUTO)
    private Long itemId;

    /**
     * 采购单 ID
     */
    private Long purchaseId;

    /**
     * 商品 ID
     */
    private Long productId;

    /**
     * 规格 ID
     */
    private Long skuId;

    /**
     * 绑定供应商 ID（供应商分拣分配依据，17.1）
     */
    private Long supplierId;

    /**
     * 需求量（订单汇总得出）
     */
    private BigDecimal requireQuantity;

    /**
     * 计划采购量
     */
    private BigDecimal purchaseQuantity;

    /**
     * 累计已收数量
     */
    private BigDecimal receivedQuantity;

    /**
     * 采购单价（不含税），可后续回填
     */
    private BigDecimal unitPrice;

    /**
     * 状态：1 待收，2 部分收，3 已收齐
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
