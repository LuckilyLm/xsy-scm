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
    PRODUCT_CODE_DUPLICATE(40923, "商品或分类编码已存在"),
    UOM_NOT_USABLE(40027, "计量单位不存在或已停用，请重新选择"),
    TAG_NOT_USABLE(40028, "商品标签不存在或已停用，请重新选择"),
    MASTER_STATUS_SALE_CONFLICT(40029, "归档商品必须处于下架状态"),
    UOM_NOT_FOUND(40421, "计量单位不存在"),
    TAG_NOT_FOUND(40422, "商品标签不存在"),
    UOM_CODE_DUPLICATE(40924, "计量单位编码已存在"),
    UOM_NAME_DUPLICATE(40925, "计量单位名称已存在，商品单位按名称记账"),
    TAG_CODE_DUPLICATE(40926, "商品标签编码已存在"),
    TAG_NAME_DUPLICATE(40927, "商品标签名称已存在"),
    UOM_REFERENCED(40928, "计量单位已被商品或供应商关系引用，不能删除，请改为停用"),
    TAG_REFERENCED(40929, "标签下仍有商品，不能删除"),
    PRODUCT_BUSINESS_REFERENCED(40931, "商品已产生业务数据，不能删除，请改为停用或归档");

    private final int code;
    private final String msg;
}
