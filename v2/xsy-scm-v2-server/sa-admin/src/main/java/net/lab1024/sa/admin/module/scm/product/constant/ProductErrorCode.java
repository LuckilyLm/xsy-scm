package net.lab1024.sa.admin.module.scm.product.constant;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.lab1024.sa.admin.module.scm.common.error.ScmErrorCode;

@Getter
@RequiredArgsConstructor
public enum ProductErrorCode implements ScmErrorCode {
    CATEGORY_NOT_FOUND(40410, "商品分类不存在"),
    CATEGORY_LEVEL_INVALID(40010, "商品分类最多支持三级"),
    CATEGORY_PARENT_INVALID(40011, "上级分类不正确"),
    CATEGORY_HAS_CHILDREN(40910, "分类下存在子分类，不能删除"),
    CATEGORY_HAS_PRODUCTS(40911, "分类下存在商品，不能删除"),
    SKU_REQUIRED(40020, "至少一个 SKU 必须保留"),
    DEFAULT_SKU_INVALID(40021, "商品必须且只能有一个默认 SKU"),
    SKU_CODE_DUPLICATE(40022, "SKU 编码重复"),
    SKU_BARCODE_DUPLICATE(40023, "SKU 条码重复"),
    SKU_SPEC_DUPLICATE(40024, "SKU 规格组合重复"),
    SKU_PRICE_INVALID(40025, "SKU 市场价不能小于零"),
    PRODUCT_NOT_FOUND(40420, "商品不存在"),
    SKU_NOT_OWNED(40920, "SKU 不属于当前商品"),
    VERSION_CONFLICT(40921, "数据已被其他操作修改，请刷新后重试"),
    IMAGE_INVALID(40026, "商品图片引用或主图设置不正确"),
    IMAGE_NOT_OWNED(40922, "图片不属于当前商品"),
    PRODUCT_CODE_DUPLICATE(40923, "商品或分类编码已存在");

    private final int code;
    private final String msg;
}
