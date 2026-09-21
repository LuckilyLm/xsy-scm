package net.lab1024.sa.admin.module.scm.supplier.constant;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.lab1024.sa.admin.module.scm.common.error.ScmErrorCode;

/**
 * 供应商域错误码。
 *
 * <p>码值沿用 legacy（40440 / 40442 / 40940 / 40942 / 40943 / 40944 / 40946），
 * 新增 40947（删除引用检查）与 40040（默认采购员）。
 *
 * <p><b>40941 已弃用</b>：legacy 用它表示「供应商 SKU 版本冲突」，W2 统一为
 * {@code ScmCommonErrorCode.VERSION_CONFLICT}(40921)。码值保留说明、不再抛出，
 * 避免历史日志出现无法解释的缺口。
 */
@Getter
@RequiredArgsConstructor
public enum SupplierErrorCode implements ScmErrorCode {

    /**
     * legacy：供应商不存在。
     */
    SUPPLIER_NOT_FOUND(40440, "供应商不存在"),

    /**
     * legacy：供应商 SKU 配置不存在。
     */
    SUPPLIER_SKU_NOT_FOUND(40442, "供应商SKU配置不存在"),

    /**
     * legacy：供应商未启用。
     */
    SUPPLIER_DISABLED(40940, "供应商未启用"),

    /**
     * legacy：SKU 未启用或不存在（SPU 与 SKU 必须同时上架）。
     */
    SKU_DISABLED(40942, "SKU 未启用或不存在"),

    /**
     * legacy：同一供应商下 SKU 重复，或请求内 skuId 重复、跨供应商 id 串用。
     */
    SUPPLIER_SKU_DUPLICATE(40943, "供应商SKU配置重复"),

    /**
     * legacy 码值，改为 Service 显式查重后抛出（不再依赖约束名字符串匹配）。
     */
    SUPPLIER_CODE_DUPLICATE(40944, "供应商编码已存在"),

    /**
     * legacy 码值，保留为 DB 唯一索引的并发兜底映射。
     */
    SUPPLIER_SKU_CONFLICT(40946, "供应商SKU配置已存在"),

    /**
     * W2 新增：删除供应商前的引用检查。
     */
    SUPPLIER_IN_USE(40947, "该供应商已被商品关联引用，不能删除"),

    /**
     * W2 新增：默认采购员不存在。
     */
    SUPPLIER_SKU_PURCHASER_INVALID(40040, "默认采购员不正确");

    private final int code;
    private final String msg;
}
