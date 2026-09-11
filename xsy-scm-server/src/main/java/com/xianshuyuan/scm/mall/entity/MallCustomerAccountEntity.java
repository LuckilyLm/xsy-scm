package com.xianshuyuan.scm.mall.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;

import java.time.OffsetDateTime;

/**
 * 商城客户登录账号。首期一个账号绑定一个客户，客户由后台确认。
 */
@Data
@TableName("mall_customer_account")
public class MallCustomerAccountEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long customerId;
    private String username;
    private String passwordHash;
    private String contactName;
    private String contactPhone;
    private String wechatOpenid;
    private String status;
    private OffsetDateTime lastLoginAt;
    @Version
    private Integer version;
    @TableLogic
    private Boolean deleted;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    private String createdBy;
    private String updatedBy;
}
