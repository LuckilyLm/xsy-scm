package com.xsy.scm.mall.account.domain.vo;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class MallProfileVO {
    private Long accountId;
    private Long customerId;
    private String customerName;
    private String loginPhone;
    private Boolean firstPasswordChange;
}
