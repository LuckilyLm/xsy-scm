package com.xsy.scm.admin.module.business.product.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 商品价格 实体类
 *
 * @author xsy-scm
 */
@Data
@TableName("t_product_price")
public class ProductPriceEntity {

    /**
     * 主键
     */
    @TableId(type = IdType.AUTO)
    private Long priceId;

    /**
     * 商品 ID
     */
    private Long productId;

    /**
     * 规格 ID，为空表示按商品定价
     */
    private Long skuId;

    /**
     * 价格类型：1 基础价，2 客户分级价，3 时价，4 协议价
     */
    private Integer priceType;

    /**
     * 客户分级 ID，分级价使用
     */
    private Long customerLevelId;

    /**
     * 指定客户 ID，协议价使用
     */
    private Long customerId;

    /**
     * 单价（不含税）
     */
    private BigDecimal price;

    /**
     * 生效时间，时价使用
     */
    private LocalDateTime effectiveTime;

    /**
     * 失效时间
     */
    private LocalDateTime expireTime;

    /**
     * 状态：1 生效，2 失效
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
