package com.xianshuyuan.scm.marketing.service;

import com.xianshuyuan.scm.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;

/**
 * 营销错误码使用 90 号域。既有号段已被 5 位编码占满，新域扩展为 6 位：
 * {@code <3 位 HTTP 状态> + <90> + <序号>}，避免与既有 5 位号段冲突。
 */
public final class MarketingErrorCodes {

    private MarketingErrorCodes() {
    }

    public static final ErrorCode INVALID_PARAM = new ErrorCode(400900, HttpStatus.BAD_REQUEST, "营销参数不正确");
    public static final ErrorCode PROMOTION_TYPE_INVALID =
            new ErrorCode(400901, HttpStatus.BAD_REQUEST, "促销活动类型不合法");
    public static final ErrorCode PROMOTION_SCOPE_INVALID =
            new ErrorCode(400902, HttpStatus.BAD_REQUEST, "促销适用范围不合法");
    public static final ErrorCode HOME_SECTION_TYPE_INVALID =
            new ErrorCode(400903, HttpStatus.BAD_REQUEST, "首页板块类型不合法");
    public static final ErrorCode COUPON_TYPE_INVALID =
            new ErrorCode(400904, HttpStatus.BAD_REQUEST, "优惠券类型不合法");
    public static final ErrorCode THEME_VALUE_INVALID =
            new ErrorCode(400905, HttpStatus.BAD_REQUEST, "商城主题配置不合法");
    public static final ErrorCode PROMOTION_NOT_FOUND =
            new ErrorCode(404900, HttpStatus.NOT_FOUND, "促销活动不存在");
    public static final ErrorCode HOME_SECTION_NOT_FOUND =
            new ErrorCode(404901, HttpStatus.NOT_FOUND, "首页板块不存在");
    public static final ErrorCode COUPON_NOT_FOUND =
            new ErrorCode(404902, HttpStatus.NOT_FOUND, "优惠券不存在");
    public static final ErrorCode COUPON_NO_NOT_FOUND =
            new ErrorCode(404903, HttpStatus.NOT_FOUND, "券码不存在");
    public static final ErrorCode COUPON_SOLD_OUT =
            new ErrorCode(409900, HttpStatus.CONFLICT, "优惠券已发完");
    public static final ErrorCode COUPON_PER_LIMIT =
            new ErrorCode(409901, HttpStatus.CONFLICT, "已超过每人限领数量");
    public static final ErrorCode COUPON_ALREADY_USED =
            new ErrorCode(409902, HttpStatus.CONFLICT, "优惠券已使用");
    public static final ErrorCode COUPON_EXPIRED =
            new ErrorCode(409903, HttpStatus.CONFLICT, "优惠券已过期");
    public static final ErrorCode COUPON_DISABLED =
            new ErrorCode(409904, HttpStatus.CONFLICT, "优惠券已停用");
}
