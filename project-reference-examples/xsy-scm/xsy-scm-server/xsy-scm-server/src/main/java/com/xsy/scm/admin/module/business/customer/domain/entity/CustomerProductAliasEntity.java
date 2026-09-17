package com.xsy.scm.admin.module.business.customer.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 客户商品别名 实体类
 *
 * @author xsy-scm
 */
@Data
@TableName("t_customer_product_alias")
public class CustomerProductAliasEntity {

    /**
     * 主键
     */
    @TableId(type = IdType.AUTO)
    private Long aliasId;

    /**
     * 客户 ID
     */
    private Long customerId;

    /**
     * 商品 ID
     */
    private Long productId;

    /**
     * 规格 ID
     */
    private Long skuId;

    /**
     * 别名
     */
    private String aliasName;

    /**
     * 别名描述（≤50 字）
     */
    private String aliasDesc;

    /**
     * 副别名
     */
    private String subAliasName;

    /**
     * 副别名描述（≤50 字）
     */
    private String subAliasDesc;

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
