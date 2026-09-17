package com.xsy.scm.admin.module.business.purchase.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 采购收货单 实体类
 *
 * <p>支持同一采购明细多次收货，累计量回写 t_purchase_item.received_quantity；
 * 支持无单收货（receive_type=2，purchase_id / item_id 为空，改记 supplier_id + product_id + sku_id）。</p>
 *
 * @author xsy-scm
 */
@Data
@TableName("t_receive")
public class ReceiveEntity {

    /**
     * 主键
     */
    @TableId(type = IdType.AUTO)
    private Long receiveId;

    /**
     * 收货单号，SHD + yyyyMMdd + 4 位流水
     */
    private String receiveNo;

    /**
     * 收货类型：1 采购收货，2 无单收货
     */
    private Integer receiveType;

    /**
     * 供应商 ID（无单收货时手工选择）
     */
    private Long supplierId;

    /**
     * 采购单 ID（无单收货时为空）
     */
    private Long purchaseId;

    /**
     * 采购明细 ID（无单收货时为空）
     */
    private Long itemId;

    /**
     * 商品 ID（无单收货时使用）
     */
    private Long productId;

    /**
     * 规格 ID（无单收货时使用）
     */
    private Long skuId;

    /**
     * 本次收货数量
     */
    private BigDecimal receiveQuantity;

    /**
     * 本次实收重量（kg），G-05 一期手工录入
     */
    private BigDecimal receiveWeight;

    /**
     * 本次单价（不含税），可传输回填
     */
    private BigDecimal unitPrice;

    /**
     * 收货人
     */
    private Long receiveBy;

    /**
     * 收货时间
     */
    private LocalDateTime receiveTime;

    /**
     * 状态：1 已收，2 已入库，3 已作废（入库后生成应付）
     */
    private Integer status;

    /**
     * 收货标记：1 正常，2 少收，3 超收（动态记录少多收，Q2）
     */
    private Integer receiveFlag;

    /**
     * 商品备注
     */
    private String remark;

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
