package com.xsy.scm.admin.module.business.product.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 商品 SPU 实体类
 *
 * @author xsy-scm
 */
@Data
@TableName("t_product")
public class ProductEntity {

    /**
     * 主键
     */
    @TableId(type = IdType.AUTO)
    private Long productId;

    /**
     * 商品编码，SP + 6 位流水
     */
    private String productNo;

    /**
     * 三级分类 ID
     */
    private Long categoryId;

    /**
     * 商品名称
     */
    private String productName;

    /**
     * 商品类型：1 标品，2 非标品
     */
    private Integer productType;

    /**
     * 计量方式：1 按件，2 按重
     */
    private Integer measureType;

    /**
     * 基本单位，如 件 / kg
     */
    private String baseUnit;

    /**
     * 是否多规格
     */
    private Boolean specFlag;

    /**
     * 采购方式：1 自采，2 供应商送货
     */
    private Integer purchaseMode;

    /**
     * 默认供应商 ID
     */
    private Long defaultSupplierId;

    /**
     * 默认采购员 ID
     */
    private Long defaultBuyerId;

    /**
     * 商品详情
     */
    private String detail;

    /**
     * 主图（文件服务地址，不硬编码公网 URL）
     */
    private String mainImage;

    /**
     * 状态：1 草稿，2 待上架，3 已上架，4 已下架，5 已作废
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
