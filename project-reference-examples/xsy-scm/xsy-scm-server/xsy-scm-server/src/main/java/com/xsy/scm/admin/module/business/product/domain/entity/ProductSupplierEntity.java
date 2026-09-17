package com.xsy.scm.admin.module.business.product.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 商品-供应商关系 实体类
 *
 * @author xsy-scm
 */
@Data
@TableName("t_product_supplier")
public class ProductSupplierEntity {

    /**
     * 主键
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 商品 ID
     */
    private Long productId;

    /**
     * 供应商 ID
     */
    private Long supplierId;

    /**
     * 供应价（不含税）
     */
    private BigDecimal supplyPrice;

    /**
     * 是否默认供应商
     */
    private Boolean defaultFlag;

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
