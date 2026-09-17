package com.xsy.scm.admin.module.business.order.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 销售订单明细 实体类
 *
 * <p>snapshotPrice 为下单时锁定的成交价快照，后续改价不影响已生成订单。</p>
 *
 * @author xsy-scm
 */
@Data
@TableName("t_order_item")
public class SaleOrderItemEntity {

    /**
     * 主键
     */
    @TableId(type = IdType.AUTO)
    private Long itemId;

    /**
     * 订单 ID
     */
    private Long orderId;

    /**
     * 商品 ID
     */
    private Long productId;

    /**
     * 规格 ID
     */
    private Long skuId;

    /**
     * 下单数量
     */
    private BigDecimal quantity;

    /**
     * 成交价快照（不含税），下单时锁定
     */
    private BigDecimal snapshotPrice;

    /**
     * 取价类型：1 基础价，2 客户分级价，3 时价，4 协议价
     */
    private Integer priceType;

    /**
     * 实际重量（kg），分拣/收货后回写
     */
    private BigDecimal actualWeight;

    /**
     * 实际数量
     */
    private BigDecimal actualQuantity;

    /**
     * 明细核算金额（不含税，按实重计算）
     */
    private BigDecimal itemAmount;

    /**
     * 明细状态：1 正常，2 已退款，3 已退货
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
