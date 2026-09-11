package com.xianshuyuan.scm.customer.service;

import com.xianshuyuan.scm.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;

public final class CustomerErrorCodes {
    public static final ErrorCode CUSTOMER_NOT_FOUND = new ErrorCode(40430, HttpStatus.NOT_FOUND, "客户不存在");
    public static final ErrorCode CUSTOMER_TYPE_NOT_FOUND = new ErrorCode(40431, HttpStatus.NOT_FOUND, "客户类型不存在");
    public static final ErrorCode AGREEMENT_PRICE_NOT_FOUND = new ErrorCode(40432, HttpStatus.NOT_FOUND, "协议价不存在");
    public static final ErrorCode CUSTOMER_TYPE_PRICE_NOT_FOUND = new ErrorCode(40433, HttpStatus.NOT_FOUND, "客户类型价不存在");
    public static final ErrorCode CUSTOMER_DISABLED = new ErrorCode(40930, HttpStatus.CONFLICT, "客户未启用");
    public static final ErrorCode SKU_NOT_VISIBLE = new ErrorCode(40931, HttpStatus.CONFLICT, "SKU 对该客户不可见或未上架");
    public static final ErrorCode VISIBILITY_NOT_OWNED = new ErrorCode(40932, HttpStatus.CONFLICT, "可见性记录不属于当前客户");
    public static final ErrorCode AGREEMENT_PRICE_OVERLAP = new ErrorCode(40933, HttpStatus.CONFLICT, "协议价有效期重叠");
    public static final ErrorCode VERSION_CONFLICT = new ErrorCode(40934, HttpStatus.CONFLICT, "数据已被其他操作修改，请刷新后重试");
    public static final ErrorCode CUSTOMER_TYPE_PRICE_OVERLAP = new ErrorCode(40935, HttpStatus.CONFLICT, "客户类型价有效期重叠");
    public static final ErrorCode PRICE_INVALID = new ErrorCode(40030, HttpStatus.BAD_REQUEST, "协议价不能小于零");
    public static final ErrorCode PERIOD_INVALID = new ErrorCode(40031, HttpStatus.BAD_REQUEST, "结束时间必须晚于开始时间");

    private CustomerErrorCodes() {
    }
}
