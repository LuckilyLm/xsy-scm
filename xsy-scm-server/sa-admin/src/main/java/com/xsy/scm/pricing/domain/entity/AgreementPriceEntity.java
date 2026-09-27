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

@Data
@TableName(value = "customer_agreement_price", autoResultMap = true)
public class AgreementPriceEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    @Version
    private Integer version = 0;
    @TableLogic(value = "false", delval = "true")
    private Boolean deleted = false;
    private Long customerId;
    private Long skuId;
    private BigDecimal unitPrice;
    private OffsetDateTime effectiveFrom;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private OffsetDateTime effectiveTo;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    private String createdBy;
    private String updatedBy;
}
