package com.xianshuyuan.scm.mall.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.OffsetDateTime;

/**
 * 商城客户会话。只保存令牌摘要，不保存明文令牌。
 */
@Data
@TableName("mall_customer_session")
public class MallCustomerSessionEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long accountId;
    private Long customerId;
    private String tokenHash;
    private String userAgent;
    private OffsetDateTime expiresAt;
    private OffsetDateTime revokedAt;
    private OffsetDateTime lastAccessedAt;
    private OffsetDateTime createdAt;
    private String createdBy;
}
