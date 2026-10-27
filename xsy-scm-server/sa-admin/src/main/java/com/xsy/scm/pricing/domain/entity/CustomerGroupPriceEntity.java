package com.xsy.scm.pricing.domain.entity;

import lombok.Data;
import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * 客户分组价：按「分组 + 商品 + 销售单位」维护，组内同一商品同一单位的规格共用一条价格。
 */
@Data
@TableName(value = "xsy_customer_group_price", autoResultMap = true)
public class CustomerGroupPriceEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    @Version
    private Integer version = 0;
    @TableLogic(value = "false", delval = "true")
    private Boolean deleted = false;
    private Long groupId;
    private Long spuId;
    private String saleUnit;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private BigDecimal unitPrice;
    private OffsetDateTime effectiveFrom;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private OffsetDateTime effectiveTo;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    private String createdBy;
    private String updatedBy;
}
