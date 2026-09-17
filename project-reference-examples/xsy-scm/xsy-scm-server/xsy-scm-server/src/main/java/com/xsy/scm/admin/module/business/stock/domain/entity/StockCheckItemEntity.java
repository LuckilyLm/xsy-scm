package com.xsy.scm.admin.module.business.stock.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 库存盘点明细 实体类
 *
 * @author xsy-scm
 */
@Data
@TableName("t_stock_check_item")
public class StockCheckItemEntity {

    /**
     * 主键
     */
    @TableId(type = IdType.AUTO)
    private Long itemId;

    /**
     * 盘点单 ID
     */
    private Long checkId;

    /**
     * 商品 ID
     */
    private Long productId;

    /**
     * 规格 ID
     */
    private Long skuId;

    /**
     * 账面数量
     */
    private BigDecimal bookQuantity;

    /**
     * 账面重量（kg）
     */
    private BigDecimal bookWeight;

    /**
     * 实盘数量
     */
    private BigDecimal actualQuantity;

    /**
     * 实盘重量（kg）
     */
    private BigDecimal actualWeight;

    /**
     * 差异数量（实盘 - 账面）
     */
    private BigDecimal diffQuantity;

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
