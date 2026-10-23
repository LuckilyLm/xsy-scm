package com.xsy.scm.mall.account.domain.vo;

import java.io.Serializable;

import lombok.Data;
import net.lab1024.sa.base.common.domain.RequestUser;
import net.lab1024.sa.base.common.enumeration.UserTypeEnum;

@Data
public class RequestMallCustomer implements RequestUser, Serializable {
    private Long accountId;
    private Integer sessionVersion;
    private Long customerId;
    private String customerName;
    private String loginPhone;
    private Boolean firstPasswordChange;
    private String ip;
    private String userAgent;

    @Override
    public Long getUserId() {
        return accountId;
    }

    @Override
    public String getUserName() {
        return customerName;
    }

    @Override
    public UserTypeEnum getUserType() {
        return UserTypeEnum.MALL_CUSTOMER;
    }
}
