package com.xsy.scm.purchase.constant;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
@Getter
@RequiredArgsConstructor
public enum ScmPurchaseCostDirectionEnum {
    INCREASE("调增"),
    DECREASE("调减");
    private final String desc;
}
