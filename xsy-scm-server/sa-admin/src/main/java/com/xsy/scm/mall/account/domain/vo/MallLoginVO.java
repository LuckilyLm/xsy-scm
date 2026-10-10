package com.xsy.scm.mall.account.domain.vo;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class MallLoginVO {
    private String token;
    private MallProfileVO profile;
}
