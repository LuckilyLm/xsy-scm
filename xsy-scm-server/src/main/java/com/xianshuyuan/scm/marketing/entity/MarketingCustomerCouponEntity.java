package com.xianshuyuan.scm.marketing.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;

import java.time.OffsetDateTime;

/**
 * 客户优惠券（发放记录）。券码全局唯一，核销时写入订单号并置为 USED。
 */
@Data
@TableName("marketing_customer_coupon")
public class MarketingCustomerCouponEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long couponId;
    private Long customerId;
    private String couponNo;
    private String status;
    private Long usedOrderId;
    private OffsetDateTime usedAt;
    private OffsetDateTime obtainedAt;
    private OffsetDateTime validFrom;
    private OffsetDateTime validTo;
    @Version
    private Integer version;
    @TableLogic
    private Boolean deleted;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    private String createdBy;
    private String updatedBy;
}
