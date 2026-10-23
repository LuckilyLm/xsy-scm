package com.xsy.scm.pricing.domain.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import lombok.Data;

/**
 * 时价：商品规格级、按时间段生效的临时价格。
 */
@Data
@TableName(value = "xsy_pricing_time_price", autoResultMap = true)
public class TimePriceEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    @Version
    private Integer version = 0;

    @TableLogic(value = "false", delval = "true")
    private Boolean deleted = false;

    private Long skuId;

    private BigDecimal unitPrice;

    private OffsetDateTime startAt;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private OffsetDateTime endAt;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String remark;

    private OffsetDateTime createdAt;

    private String createdBy;

    private OffsetDateTime updatedAt;

    private String updatedBy;
}
