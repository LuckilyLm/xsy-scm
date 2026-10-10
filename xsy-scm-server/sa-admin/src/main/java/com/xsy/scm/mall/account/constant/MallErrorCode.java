package com.xsy.scm.mall.account.constant;

import com.xsy.scm.common.error.ScmErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum MallErrorCode implements ScmErrorCode {
    ACCOUNT_NOT_FOUND(40480, "商城账号不存在"),
    LOGIN_FAILED(40080, "手机号或密码错误"),
    ACCOUNT_DISABLED(40980, "商城账号已禁用"),
    PHONE_DUPLICATE(40981, "登录手机号已被使用"),
    PASSWORD_CHANGE_REQUIRED(40983, "请先修改临时密码"),
    PASSWORD_INVALID(40081, "密码必须为8至64个字符"),
    CATALOG_PRODUCT_NOT_FOUND(40481, "商品不存在"),
    CART_SKU_NOT_VISIBLE(40482, "商品不可售或对当前客户不可见"),
    CART_ITEM_NOT_FOUND(40483, "购物车中没有该商品"),
    CART_QUANTITY_INVALID(40082, "数量必须为大于0、最多4位小数的数字"),
    ADDRESS_NOT_FOUND(40484, "收货地址不存在"),
    ADDRESS_COORDINATE_INVALID(40083, "经纬度必须成对提供"),
    CHECKOUT_EMPTY(40084, "请选择要结算的商品"),
    CHECKOUT_ADDRESS_REQUIRED(40085, "请选择收货地址"),
    CHECKOUT_IDEMPOTENCY_REQUIRED(40086, "缺少幂等键，无法安全提交订单"),
    CHECKOUT_ITEM_UNAVAILABLE(40984, "所选商品中有不可售项，请返回购物车调整"),
    CHECKOUT_PRICE_CHANGED(40985, "商品价格已变化，请重新确认结算金额");

    private final int code;
    private final String msg;
}
