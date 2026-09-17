package com.xsy.scm.admin.module.business.product.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 商品条码 实体类
 *
 * @author xsy-scm
 */
@Data
@TableName("t_product_barcode")
public class ProductBarcodeEntity {

    /**
     * 主键
     */
    @TableId(type = IdType.AUTO)
    private Long barcodeId;

    /**
     * 商品 ID
     */
    private Long productId;

    /**
     * 规格 ID
     */
    private Long skuId;

    /**
     * 条形码（唯一）
     */
    private String barcode;

    /**
     * 对应单位
     */
    private String unit;

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
