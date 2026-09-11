package com.xianshuyuan.scm.mall.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;

import java.time.OffsetDateTime;

@Data
@TableName("mall_customer_address")
public class MallCustomerAddressEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long customerId;
    private String receiverName;
    private String phone;
    private String region;
    private String detailAddress;
    @TableField("is_default")
    private Boolean defaultAddress;
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
