package com.xsy.scm.admin.module.business.order.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 订单操作日志 实体类
 *
 * <p>日志类表，不做逻辑删除（与 t_stock_flow 一致）。</p>
 *
 * @author xsy-scm
 */
@Data
@TableName("t_order_log")
public class SaleOrderLogEntity {

    /**
     * 主键
     */
    @TableId(type = IdType.AUTO)
    private Long logId;

    /**
     * 订单 ID
     */
    private Long orderId;

    /**
     * 操作类型：1 创建，2 确认，3 改价，4 编辑，5 取消，6 发货，7 签收，8 核算，9 退款，10 作废
     */
    private Integer operateType;

    /**
     * 修改前值
     */
    private String beforeValue;

    /**
     * 修改后值
     */
    private String afterValue;

    /**
     * 操作人
     */
    private Long operateBy;

    /**
     * 操作时间
     */
    private LocalDateTime operateTime;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;
}
