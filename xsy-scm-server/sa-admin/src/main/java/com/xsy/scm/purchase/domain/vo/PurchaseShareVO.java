package com.xsy.scm.purchase.domain.vo;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.time.OffsetDateTime;

import lombok.Data;

/**
 * 采购单分享链接（后台侧）。
 *
 * <p>
 * 令牌明文只在生成时返回一次，列表里只回显前缀；数据库只存哈希。
 */
@Data
public class PurchaseShareVO {

    private Long id;
    private Long purchaseOrderId;

    /**
     * 令牌哈希；只用于写入与查找，永不下发给客户端。
     */
    @JsonIgnore
    private String tokenHash;
    /**
     * 仅在生成响应里出现；列表查询为 null。
     */
    private String token;
    private String tokenPrefix;
    private OffsetDateTime expiresAt;
    private OffsetDateTime revokedAt;
    private OffsetDateTime lastUsedAt;
    private Integer useCount;
    private String createdBy;
    private OffsetDateTime createdAt;
}
