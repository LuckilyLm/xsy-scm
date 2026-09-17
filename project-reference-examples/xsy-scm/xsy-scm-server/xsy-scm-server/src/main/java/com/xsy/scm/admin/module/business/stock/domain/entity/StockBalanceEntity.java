package com.xsy.scm.admin.module.business.stock.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 库存余额 实体类
 *
 * <p>余额由 t_stock_flow 流水推导，任何模块不得直接 UPDATE 余额，必须走库存业务层。
 * 加权平均成本在每次入库时实时重算（06-04 已定）。</p>
 *
 * @author xsy-scm
 */
@Data
@TableName("t_stock_balance")
public class StockBalanceEntity {

    /**
     * 主键
     */
    @TableId(type = IdType.AUTO)
    private Long balanceId;

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
     * 批次 ID，G-03 不启用批次，字段保留备用
     */
    private Long batchId;

    /**
     * 当前数量
     */
    private BigDecimal quantity;

    /**
     * 当前重量（kg）
     */
    private BigDecimal weight;

    /**
     * 加权平均成本单价（不含税），每次入库实时重算
     */
    private BigDecimal avgCost;

    /**
     * 结存总成本（不含税）
     */
    private BigDecimal totalCost;

    /**
     * 预警下限
     */
    private BigDecimal warnMin;

    /**
     * 预警上限
     */
    private BigDecimal warnMax;

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
