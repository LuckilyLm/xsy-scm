package com.xianshuyuan.scm.product.service;

import com.xianshuyuan.scm.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;

public final class ProductErrorCodes {

    public static final ErrorCode CATEGORY_NOT_FOUND =
        new ErrorCode(40410, HttpStatus.NOT_FOUND, "商品分类不存在");
    public static final ErrorCode CATEGORY_LEVEL_INVALID =
        new ErrorCode(40010, HttpStatus.BAD_REQUEST, "商品分类最多支持三级");
    public static final ErrorCode CATEGORY_PARENT_INVALID =
        new ErrorCode(40011, HttpStatus.BAD_REQUEST, "上级分类不正确");
    public static final ErrorCode CATEGORY_HAS_CHILDREN =
        new ErrorCode(40910, HttpStatus.CONFLICT, "分类下存在子分类，不能删除");
    public static final ErrorCode CATEGORY_HAS_PRODUCTS =
        new ErrorCode(40911, HttpStatus.CONFLICT, "分类下存在商品，不能删除");

    private ProductErrorCodes() {
    }
}
