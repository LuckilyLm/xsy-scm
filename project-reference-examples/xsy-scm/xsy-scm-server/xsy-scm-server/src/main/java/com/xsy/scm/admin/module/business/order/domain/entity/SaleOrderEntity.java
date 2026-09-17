package com.xsy.scm.admin.module.business.order.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 销售订单 实体类
 *
 * <p>表名为 t_order：本模块统一以「销售订单 SaleOrder」语义命名，避免与 SQL 关键字概念混淆。</p>
 *
 * @author xsy-scm
 */
@Data
@TableName("t_order")
public class SaleOrderEntity {

    /**
     * 主键
     */
    @TableId(type = IdType.AUTO)
    private Long orderId;

    /**
     * 订单号，XSD + yyyyMMdd + 4 位流水
     */
    private String orderNo;

    /**
     * 下单客户 ID（下属单位下单时为本单位）
     */
    private Long customerId;

    /**
     * 结算客户 ID（集团统一结算时为集团客户）
     */
    private Long settleCustomerId;

    /**
     * 来源：1 商城下单，2 后台录单，3 补单
     */
    private Integer source;

    /**
     * 结算方式：1 账期支付，2 货到付款，3 在线支付，4 余额充值
     */
    private Integer settleType;

    /**
     * 下单金额（不含税，快照价计算）
     */
    private BigDecimal totalAmount;

    /**
     * 优惠金额（不含税）
     */
    private BigDecimal discountAmount;

    /**
     * 应付金额（不含税）
     */
    private BigDecimal payableAmount;

    /**
     * 核算金额（不含税，按实重核算后）
     */
    private BigDecimal actualAmount;

    /**
     * 支付状态：1 未付，2 部分支付，3 已付
     */
    private Integer payStatus;

    /**
     * 期望配送时间
     */
    private LocalDateTime expectDeliveryTime;

    /**
     * 归属业务员 ID
     */
    private Long sellerId;

    /**
     * 状态：1 草稿，2 待确认，3 已确认，4 采购中，5 待分拣，6 分拣中，7 配送中，8 已签收，9 已完成，10 退款中，11 已取消，12 已作废
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
     * 删除状态：0 否，1 是（订单不允许物理删除）
     */
    private Boolean deletedFlag;
}
