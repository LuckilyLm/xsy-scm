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
    public static final ErrorCode SKU_REQUIRED =
        new ErrorCode(40020, HttpStatus.BAD_REQUEST, "至少一个 SKU 必须保留");
    public static final ErrorCode DEFAULT_SKU_INVALID =
        new ErrorCode(40021, HttpStatus.BAD_REQUEST, "商品必须且只能有一个默认 SKU");
    public static final ErrorCode SKU_CODE_DUPLICATE =
        new ErrorCode(40022, HttpStatus.BAD_REQUEST, "SKU 编码重复");
    public static final ErrorCode SKU_BARCODE_DUPLICATE =
        new ErrorCode(40023, HttpStatus.BAD_REQUEST, "SKU 条码重复");
    public static final ErrorCode SKU_SPEC_DUPLICATE =
        new ErrorCode(40024, HttpStatus.BAD_REQUEST, "SKU 规格组合重复");
    public static final ErrorCode SKU_PRICE_INVALID =
        new ErrorCode(40025, HttpStatus.BAD_REQUEST, "SKU 市场价不能小于零");
    public static final ErrorCode PRODUCT_NOT_FOUND =
        new ErrorCode(40420, HttpStatus.NOT_FOUND, "商品不存在");
    public static final ErrorCode SKU_NOT_OWNED =
        new ErrorCode(40920, HttpStatus.CONFLICT, "SKU 不属于当前商品");
    public static final ErrorCode VERSION_CONFLICT =
        new ErrorCode(40921, HttpStatus.CONFLICT, "数据已被其他操作修改，请刷新后重试");

    private ProductErrorCodes() {
    }
}
