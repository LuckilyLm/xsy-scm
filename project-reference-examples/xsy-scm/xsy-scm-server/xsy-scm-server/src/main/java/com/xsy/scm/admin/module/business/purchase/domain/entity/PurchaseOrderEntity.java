package com.xsy.scm.admin.module.business.purchase.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 采购单 实体类
 *
 * @author xsy-scm
 */
@Data
@TableName("t_purchase_order")
public class PurchaseOrderEntity {

    /**
     * 主键
     */
    @TableId(type = IdType.AUTO)
    private Long purchaseId;

    /**
     * 采购单号，CGD + yyyyMMdd + 4 位流水
     */
    private String purchaseNo;

    /**
     * 供应商 ID
     */
    private Long supplierId;

    /**
     * 采购员 ID
     */
    private Long buyerId;

    /**
     * 品类 ID，按品类汇总时使用
     */
    private Long categoryId;

    /**
     * 采购预估金额（不含税）
     */
    private BigDecimal totalAmount;

    /**
     * 实际采购金额（不含税，按实重收货后）
     */
    private BigDecimal actualAmount;

    /**
     * 期望到货时间
     */
    private LocalDateTime expectArriveTime;

    /**
     * 采购单二维码（文件服务，不硬编码公网 URL）
     */
    private String qrcodeUrl;

    /**
     * 状态：1 待接单，2 采购中，3 部分收货，4 已完成，5 已取消
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
     * 删除状态：0 否，1 是（采购单不允许物理删除）
     */
    private Boolean deletedFlag;
}
