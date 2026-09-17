package com.xsy.scm.admin.module.business.stock.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 库存流水 实体类
 *
 * <p>流水类表，不做逻辑删除，冲销使用反向流水。
 * 记录变动前后的数量与加权平均成本，保证可追溯。</p>
 *
 * @author xsy-scm
 */
@Data
@TableName("t_stock_flow")
public class StockFlowEntity {

    /**
     * 主键
     */
    @TableId(type = IdType.AUTO)
    private Long flowId;

    /**
     * 流水号
     */
    private String flowNo;

    /**
     * 商品 ID
     */
    private Long productId;

    /**
     * 规格 ID
     */
    private Long skuId;

    /**
     * 仓库 ID，G-03 单仓库，字段保留备用
     */
    private Long warehouseId;

    /**
     * 批次 ID，G-03 不启用，字段保留备用
     */
    private Long batchId;

    /**
     * 流水类型：1 采购入库，2 销售出库，3 退货入库，4 报损，5 报溢，6 盘点调整，7 规格转换出，8 规格转换入
     */
    private Integer flowType;

    /**
     * 关联业务：1 采购，2 订单，3 分拣，4 盘点，5 报损报溢，6 规格转换
     */
    private Integer bizType;

    /**
     * 关联业务单 ID
     */
    private Long bizId;

    /**
     * 方向：1 入，2 出
     */
    private Integer direction;

    /**
     * 变动数量（正数）
     */
    private BigDecimal quantity;

    /**
     * 变动重量（kg，正数）
     */
    private BigDecimal weight;

    /**
     * 变动单价（不含税）
     */
    private BigDecimal unitPrice;

    /**
     * 变动金额（不含税）
     */
    private BigDecimal amount;

    /**
     * 变动前数量
     */
    private BigDecimal beforeQuantity;

    /**
     * 变动后数量
     */
    private BigDecimal afterQuantity;

    /**
     * 变动前加权平均成本
     */
    private BigDecimal beforeAvgCost;

    /**
     * 变动后加权平均成本
     */
    private BigDecimal afterAvgCost;

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
