package com.xsy.scm.admin.module.business.stock.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 商品转换单 实体类
 *
 * @author xsy-scm
 */
@Data
@TableName("t_product_convert")
public class ProductConvertEntity {

    /**
     * 主键
     */
    @TableId(type = IdType.AUTO)
    private Long convertId;

    /**
     * 转换单号，ZHD + yyyyMMdd + 4 位流水
     */
    private String convertNo;

    /**
     * 转换类型：1 整件拆零，2 组合拆分
     */
    private Integer convertType;

    /**
     * 仓库 ID（单仓库，字段保留）
     */
    private Long warehouseId;

    /**
     * 来源：1 手工创建，2 发货差异表批量转换
     */
    private Integer sourceType;

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
