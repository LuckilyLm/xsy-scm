package com.xsy.scm.mall.account.constant;

import com.xsy.scm.common.error.ScmErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum MallErrorCode implements ScmErrorCode {
    ACCOUNT_NOT_FOUND(40480, "商城账号不存在"),
    LOGIN_FAILED(40080, "手机号或密码错误"),
    LOGIN_LOCKED(42980, "登录失败次数过多，账号已暂时锁定，请稍后重试"),
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
    CHECKOUT_SETTLEMENT_METHOD_REQUIRED(40087, "请选择结算方式后再提交订单"),
    CHECKOUT_ITEM_UNAVAILABLE(40984, "所选商品中有不可售项，请返回购物车调整"),
    CHECKOUT_PRICE_CHANGED(40985, "商品价格已变化，请重新确认结算金额"),
    ORDER_NOT_FOUND(40489, "订单不存在"),
    ORDER_NOT_CANCELABLE(40986, "当前订单状态不允许取消，请联系业务员处理"),
    ORDER_ALREADY_FULFILLING(40987, "订单已进入配送流程，无法取消，请联系业务员处理"),
    REORDER_NOTHING_ADDED(40988, "历史商品均已下架或暂无客户价，未能加入购物车"),
    AFTERSALE_NOT_FOUND(40485, "售后单不存在"),
    AFTERSALE_NOT_CANCELABLE(40989, "当前售后状态不允许撤销"),
    FUNDS_RECEIVABLE_NOT_FOUND(40490, "账单不存在"),
    CHECKOUT_SETTLEMENT_METHOD_UNAVAILABLE(40991, "所选结算方式当前不可用，请重新选择"),
    ORDER_NOT_PAYABLE(40990, "当前订单状态不允许支付"),
    ORDER_ALREADY_PAID(40992, "订单已支付，请勿重复付款"),
    ORDER_SETTLEMENT_NOT_WALLET(40993, "该订单未选择余额结算");

    private final int code;
    private final String msg;
}
