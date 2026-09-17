package com.xsy.scm.admin.module.business.stock.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 库存调整单 实体类（报损 / 报溢 / 盘点调整 / 规格转换）
 *
 * @author xsy-scm
 */
@Data
@TableName("t_stock_adjust")
public class StockAdjustEntity {

    /**
     * 主键
     */
    @TableId(type = IdType.AUTO)
    private Long adjustId;

    /**
     * 调整单号，BSD 报损 / BYD 报溢 / ZHD 转换 + yyyyMMdd + 4 位流水
     */
    private String adjustNo;

    /**
     * 调整类型：1 报损，2 报溢，3 盘点调整，4 规格转换
     */
    private Integer adjustType;

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
     * 调整数量（正数）
     */
    private BigDecimal quantity;

    /**
     * 调整重量（kg，正数）
     */
    private BigDecimal weight;

    /**
     * 调整原因
     */
    private String reason;

    /**
     * 状态：1 待审核，2 已完成，3 已驳回
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
