package com.xsy.scm.admin.module.business.customer.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 客户折扣率（计算折前价） 实体类
 *
 * @author xsy-scm
 */
@Data
@TableName("t_customer_discount")
public class CustomerDiscountEntity {

    /**
     * 主键
     */
    @TableId(type = IdType.AUTO)
    private Long discountId;

    /**
     * 客户 ID
     */
    private Long customerId;

    /**
     * 折扣范围：1 统一折扣，2 按商品，3 按分类
     */
    private Integer scopeType;

    /**
     * 商品 ID（scopeType=2 时使用）
     */
    private Long productId;

    /**
     * 分类 ID（scopeType=3 时使用）
     */
    private Long categoryId;

    /**
     * 折扣率（0~1，1 表示不打折）
     */
    private BigDecimal discountRate;

    /**
     * 状态：1 生效，2 停用
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
