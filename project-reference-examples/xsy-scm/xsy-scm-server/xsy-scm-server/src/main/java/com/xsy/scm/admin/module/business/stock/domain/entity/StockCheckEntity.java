package com.xsy.scm.admin.module.business.stock.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 库存盘点单 实体类
 *
 * @author xsy-scm
 */
@Data
@TableName("t_stock_check")
public class StockCheckEntity {

    /**
     * 主键
     */
    @TableId(type = IdType.AUTO)
    private Long checkId;

    /**
     * 盘点单号，PDD + yyyyMMdd + 4 位流水
     */
    private String checkNo;

    /**
     * 仓库 ID，G-03 单仓库，字段保留备用
     */
    private Long warehouseId;

    /**
     * 盘点类型：1 全盘，2 抽盘
     */
    private Integer checkType;

    /**
     * 状态：1 待盘点，2 盘点中，3 已完成，4 已取消
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
