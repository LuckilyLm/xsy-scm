package com.xsy.scm.admin.module.business.product.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 商品规格 SKU 实体类
 *
 * @author xsy-scm
 */
@Data
@TableName("t_product_sku")
public class ProductSkuEntity {

    /**
     * 主键
     */
    @TableId(type = IdType.AUTO)
    private Long skuId;

    /**
     * 所属商品 ID
     */
    private Long productId;

    /**
     * 规格编码
     */
    private String skuNo;

    /**
     * 规格名称，如 规格/单位 组合
     */
    private String specName;

    /**
     * 销售单位
     */
    private String unit;

    /**
     * 单件折算重量（kg），非标品换算用
     */
    private BigDecimal unitWeight;

    /**
     * 状态：1 启用，2 停用
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
