package com.xianshuyuan.scm.purchase.service;

import com.xianshuyuan.scm.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;

public final class PurchaseDemandErrorCodes {
    public static final ErrorCode NOT_FOUND =
            new ErrorCode(40450, HttpStatus.NOT_FOUND, "采购需求不存在");
    public static final ErrorCode SOURCE_INVALID =
            new ErrorCode(40950, HttpStatus.CONFLICT, "销售订单状态不允许生成采购需求");
    public static final ErrorCode VERSION_CONFLICT =
            new ErrorCode(40951, HttpStatus.CONFLICT, "采购需求版本冲突");
    public static final ErrorCode ALLOCATION_CONFLICT =
            new ErrorCode(40952, HttpStatus.CONFLICT, "采购需求分配冲突");
    public static final ErrorCode ALREADY_EXISTS =
            new ErrorCode(40956, HttpStatus.CONFLICT, "采购需求已存在");
    public static final ErrorCode OVER_ALLOCATED =
            new ErrorCode(40050, HttpStatus.BAD_REQUEST, "分配数量超过需求");

    private PurchaseDemandErrorCodes() {
    }
}
