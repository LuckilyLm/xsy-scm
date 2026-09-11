package com.xianshuyuan.scm.mall.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.OffsetDateTime;

/**
 * 商城订单的收货地址快照。下单时保存，后续修改地址不影响历史订单。
 */
@Data
@TableName("mall_order_address")
public class MallOrderAddressEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long orderId;
    private Long customerId;
    private String receiverName;
    private String phone;
    private String region;
    private String detailAddress;
    private OffsetDateTime createdAt;
    private String createdBy;
}
