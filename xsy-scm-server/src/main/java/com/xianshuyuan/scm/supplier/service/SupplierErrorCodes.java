package com.xianshuyuan.scm.supplier.service;

import com.xianshuyuan.scm.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;

public final class SupplierErrorCodes {
    public static final ErrorCode SUPPLIER_NOT_FOUND = new ErrorCode(40440, HttpStatus.NOT_FOUND, "供应商不存在");
    public static final ErrorCode WAREHOUSE_NOT_FOUND = new ErrorCode(40441, HttpStatus.NOT_FOUND, "仓库不存在");
    public static final ErrorCode SUPPLIER_SKU_NOT_FOUND = new ErrorCode(40442, HttpStatus.NOT_FOUND, "供应商 SKU 配置不存在");
    public static final ErrorCode DISABLED = new ErrorCode(40940, HttpStatus.CONFLICT, "资料未启用");
    public static final ErrorCode VERSION_CONFLICT = new ErrorCode(40941, HttpStatus.CONFLICT, "数据已被其他操作修改，请刷新后重试");
    public static final ErrorCode SKU_DISABLED = new ErrorCode(40942, HttpStatus.CONFLICT, "SKU 未启用或不存在");
    public static final ErrorCode SUPPLIER_SKU_DUPLICATE = new ErrorCode(40943, HttpStatus.CONFLICT, "供应商 SKU 配置重复");
    public static final ErrorCode SUPPLIER_CODE_CONFLICT = new ErrorCode(40944, HttpStatus.CONFLICT, "供应商编码已存在");
    public static final ErrorCode WAREHOUSE_CODE_CONFLICT = new ErrorCode(40945, HttpStatus.CONFLICT, "仓库编码已存在");
    public static final ErrorCode SUPPLIER_SKU_UNIQUE_CONFLICT = new ErrorCode(40946, HttpStatus.CONFLICT, "供应商 SKU 配置已存在");

    private SupplierErrorCodes() {
    }
}
