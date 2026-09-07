package com.xianshuyuan.scm.purchase.service;

import com.xianshuyuan.scm.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;

public final class PurchaseOrderErrorCodes {
    public static final ErrorCode NOT_FOUND =
            new ErrorCode(40452, HttpStatus.NOT_FOUND, "采购单不存在");
    public static final ErrorCode VERSION_CONFLICT =
            new ErrorCode(40955, HttpStatus.CONFLICT, "采购单版本冲突");
    public static final ErrorCode INVALID_STATE =
            new ErrorCode(40953, HttpStatus.CONFLICT, "采购单状态不允许此操作");
    public static final ErrorCode DEMAND_REPLACEMENT_NOT_ALLOWED =
            new ErrorCode(40954, HttpStatus.CONFLICT, "保留采购行不允许替换采购需求来源");
    public static final ErrorCode INVALID_QUANTITY =
            new ErrorCode(40051, HttpStatus.BAD_REQUEST, "采购数量格式不正确");

    private PurchaseOrderErrorCodes() {
    }
}
