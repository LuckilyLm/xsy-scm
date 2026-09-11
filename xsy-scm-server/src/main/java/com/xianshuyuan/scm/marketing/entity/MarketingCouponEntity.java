package com.xianshuyuan.scm.marketing.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * 优惠券模板。发放时按模板生成客户优惠券，issued_quantity 不得超过 total_quantity。
 */
@Data
@TableName("marketing_coupon")
public class MarketingCouponEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    private String couponType;
    private BigDecimal thresholdAmount;
    private BigDecimal discountRate;
    private BigDecimal reduceAmount;
    private Integer totalQuantity;
    private Integer issuedQuantity;
    private Integer perLimit;
    private OffsetDateTime validFrom;
    private OffsetDateTime validTo;
    private String status;
    @Version
    private Integer version;
    @TableLogic
    private Boolean deleted;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    private String createdBy;
    private String updatedBy;
}
