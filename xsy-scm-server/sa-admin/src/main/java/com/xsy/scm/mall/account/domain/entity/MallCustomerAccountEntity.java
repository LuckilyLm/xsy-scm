package com.xsy.scm.mall.account.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import java.time.OffsetDateTime;
import lombok.Data;

@Data
@TableName("mall_customer_account")
public class MallCustomerAccountEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long customerId;
    private String loginPhone;
    private String passwordHash;
    private String status;
    private Boolean firstPasswordChange;
    private Integer loginFailedCount;
    private OffsetDateTime lockedUntil;
    private OffsetDateTime lastLoginAt;
    private String lastLoginIp;
    @Version
    private Integer version;
    @TableLogic(value = "false", delval = "true")
    private Boolean deleted;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    private String createdBy;
    private String updatedBy;
}
