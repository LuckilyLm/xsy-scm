package com.xsy.scm.admin.module.business.supplier.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 供应商商品提报 实体类
 *
 * @author xsy-scm
 */
@Data
@TableName("t_supplier_product_apply")
public class SupplierProductApplyEntity {

    /**
     * 主键
     */
    @TableId(type = IdType.AUTO)
    private Long applyId;

    /**
     * 提报单号，SPB + yyyyMMdd + 4 位流水
     */
    private String applyNo;

    /**
     * 供应商 ID
     */
    private Long supplierId;

    /**
     * 商品名称
     */
    private String productName;

    /**
     * 商品别名（≤20 字）
     */
    private String alias;

    /**
     * 拟归类 ID
     */
    private Long categoryId;

    /**
     * 供货价（不含税）
     */
    private BigDecimal supplyPrice;

    /**
     * 最近一次进价（不含税，受权限控制）
     */
    private BigDecimal lastPurchasePrice;

    /**
     * 商品图片（文件服务）
     */
    private String image;

    /**
     * 审核状态：1 待审核，2 已通过，3 已驳回
     */
    private Integer auditStatus;

    /**
     * 驳回原因
     */
    private String rejectReason;

    /**
     * 审核通过后生成的商品 ID
     */
    private Long productId;

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
