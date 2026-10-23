package com.xsy.scm.mall.account.domain.vo;

import java.time.OffsetDateTime;

import lombok.Data;

@Data
public class MallAccountVO {
    private Long accountId;
    private Long customerId;
    private String customerName;
    private String loginPhone;
    private String status;
    private Boolean firstPasswordChange;
    private OffsetDateTime lastLoginAt;
    private Integer version;
}
