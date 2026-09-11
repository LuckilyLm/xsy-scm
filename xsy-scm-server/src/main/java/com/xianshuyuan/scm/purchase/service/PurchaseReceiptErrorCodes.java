package com.xianshuyuan.scm.purchase.service;

import com.xianshuyuan.scm.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;

public final class PurchaseReceiptErrorCodes {
    public static final ErrorCode NOT_FOUND = new ErrorCode(40460, HttpStatus.NOT_FOUND, "收货单不存在");
    public static final ErrorCode ALREADY_EXISTS = new ErrorCode(40960, HttpStatus.CONFLICT, "采购订单已存在收货单");
    public static final ErrorCode INVALID_STATE = new ErrorCode(40961, HttpStatus.CONFLICT, "收货单状态不允许此操作");
    public static final ErrorCode VERSION_CONFLICT = new ErrorCode(40962, HttpStatus.CONFLICT, "收货单版本冲突");
    public static final ErrorCode ITEM_NOT_FOUND = new ErrorCode(40461, HttpStatus.NOT_FOUND, "收货行不存在");
    public static final ErrorCode OVER_RECEIVED = new ErrorCode(40963, HttpStatus.CONFLICT, "本次收货数量超过剩余可收数量");
    public static final ErrorCode INVALID_QUANTITY = new ErrorCode(40060, HttpStatus.BAD_REQUEST, "收货数量或重量不正确");
    public static final ErrorCode INVALID_TOLERANCE = new ErrorCode(50060, HttpStatus.INTERNAL_SERVER_ERROR, "采购超收容差系统参数无效");

    private PurchaseReceiptErrorCodes() {
    }
}
