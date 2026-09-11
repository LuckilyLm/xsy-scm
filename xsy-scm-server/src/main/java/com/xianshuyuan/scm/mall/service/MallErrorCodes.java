package com.xianshuyuan.scm.mall.service;

import com.xianshuyuan.scm.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;

/**
 * 商城错误码使用 07 号域：40070 / 40170 / 40370 / 40470 / 40970，避免与其他业务域冲突。
 */
public final class MallErrorCodes {

    private MallErrorCodes() {
    }

    public static final ErrorCode LOGIN_FAILED = new ErrorCode(40170, HttpStatus.UNAUTHORIZED, "账号或密码不正确");
    public static final ErrorCode LOGIN_REQUIRED = new ErrorCode(40171, HttpStatus.UNAUTHORIZED, "请先登录");
    public static final ErrorCode TOKEN_INVALID = new ErrorCode(40172, HttpStatus.UNAUTHORIZED, "登录已失效，请重新登录");
    public static final ErrorCode ACCOUNT_DISABLED = new ErrorCode(40370, HttpStatus.FORBIDDEN, "商城账号已停用，请联系客服");
    public static final ErrorCode CUSTOMER_DISABLED = new ErrorCode(40371, HttpStatus.FORBIDDEN, "客户已停用，请联系客服");
    public static final ErrorCode SKU_NOT_VISIBLE = new ErrorCode(40372, HttpStatus.FORBIDDEN, "当前账号不可购买该商品");
    public static final ErrorCode SKU_NOT_FOUND = new ErrorCode(40470, HttpStatus.NOT_FOUND, "商品不存在或已下架");
    public static final ErrorCode CART_EMPTY = new ErrorCode(40070, HttpStatus.BAD_REQUEST, "购物车为空");
    public static final ErrorCode INVALID_QUANTITY = new ErrorCode(40071, HttpStatus.BAD_REQUEST, "数量必须大于零");
    public static final ErrorCode QUANTITY_OUT_OF_RANGE = new ErrorCode(40072, HttpStatus.BAD_REQUEST, "数量超出允许范围");
    public static final ErrorCode ADDRESS_NOT_FOUND = new ErrorCode(40471, HttpStatus.NOT_FOUND, "收货地址不存在");
    public static final ErrorCode ADDRESS_REQUIRED = new ErrorCode(40073, HttpStatus.BAD_REQUEST, "请选择收货地址");
    public static final ErrorCode ORDER_ITEMS_REQUIRED = new ErrorCode(40074, HttpStatus.BAD_REQUEST, "请先选择要下单的商品");
    public static final ErrorCode ORDER_NOT_FOUND = new ErrorCode(40472, HttpStatus.NOT_FOUND, "订单不存在");
    public static final ErrorCode TRACE_NOT_FOUND = new ErrorCode(40473, HttpStatus.NOT_FOUND, "溯源码不存在");
    public static final ErrorCode PRICE_CHANGED = new ErrorCode(40970, HttpStatus.CONFLICT, "商品价格已变化，请确认后重新提交");
    public static final ErrorCode WECHAT_LOGIN_UNAVAILABLE =
            new ErrorCode(50170, HttpStatus.NOT_IMPLEMENTED, "微信登录尚未开通，请使用账号密码登录");
}
