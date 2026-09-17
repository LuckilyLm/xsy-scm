package com.xsy.scm.admin.module.business.stock.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 商品转换明细 实体类
 *
 * <p>一行表示「一个原商品 → 一个目标商品」；一品转多品时同一原商品有多行。</p>
 *
 * @author xsy-scm
 */
@Data
@TableName("t_product_convert_item")
public class ProductConvertItemEntity {

    /**
     * 主键
     */
    @TableId(type = IdType.AUTO)
    private Long itemId;

    /**
     * 转换单 ID
     */
    private Long convertId;

    /**
     * 原商品 ID
     */
    private Long sourceProductId;

    /**
     * 原规格 ID
     */
    private Long sourceSkuId;

    /**
     * 出库数量（扣减原商品）
     */
    private BigDecimal sourceQuantity;

    /**
     * 出库重量 kg
     */
    private BigDecimal sourceWeight;

    /**
     * 目标商品 ID
     */
    private Long targetProductId;

    /**
     * 目标规格 ID
     */
    private Long targetSkuId;

    /**
     * 入库数量
     */
    private BigDecimal targetQuantity;

    /**
     * 入库重量 kg
     */
    private BigDecimal targetWeight;

    /**
     * 入库单价（不含税），生成新商品成本
     */
    private BigDecimal targetUnitPrice;

    /**
     * 入库金额（不含税）
     */
    private BigDecimal targetAmount;

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
