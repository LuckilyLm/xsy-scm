package com.xsy.scm.mall.account.domain.vo;

import java.io.Serializable;
import com.xsy.scm.mall.account.constant.MallLoginMethodEnum;

import lombok.Data;
import com.xsy.scm.base.common.domain.RequestUser;
import com.xsy.scm.base.common.enumeration.UserTypeEnum;

@Data
public class RequestMallCustomer implements RequestUser, Serializable {
    private Long accountId;
    private Integer sessionVersion;
    private Long customerId;
    private String customerName;
    private String loginPhone;
    private Boolean firstPasswordChange;
    private MallLoginMethodEnum loginMethod = MallLoginMethodEnum.PASSWORD;
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
