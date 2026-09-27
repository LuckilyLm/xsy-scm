package com.xsy.scm.supplier.constant;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import com.xsy.scm.common.error.ScmErrorCode;

/**
 * 供应商域错误码。
 *
 * <p>
 * 稳定保留已有业务码，并使用 40947 表示供应商引用冲突、40040 表示默认采购员无效。
 *
 * <p>
 * <b>40941 已弃用</b>：SKU 版本冲突统一使用 {@code ScmCommonErrorCode.VERSION_CONFLICT}(40921)。保留该码值以便解释已有日志，不再抛出。
 */
@Getter
@RequiredArgsConstructor
public enum SupplierErrorCode implements ScmErrorCode {

    /**
     * 供应商不存在。
     */
    SUPPLIER_NOT_FOUND(
            40440,
            "供应商不存在"),

    /**
     * 供应商 SKU 配置不存在。
     */
    SUPPLIER_SKU_NOT_FOUND(
            40442,
            "供应商SKU配置不存在"),

    /**
     * 供应商未启用。
     */
    SUPPLIER_DISABLED(
            40940,
            "供应商未启用"),

    /**
     * SKU 未启用或不存在（SPU 与 SKU 必须同时上架）。
     */
    SKU_DISABLED(
            40942,
            "SKU 未启用或不存在"),

    /**
     * 同一供应商下 SKU 重复，或请求内 skuId 重复、跨供应商 id 串用。
     */
    SUPPLIER_SKU_DUPLICATE(
            40943,
            "供应商SKU配置重复"),

    /**
     * 供应商编码重复；Service 先显式查重，并将数据库并发冲突映射到此码。
     */
    SUPPLIER_CODE_DUPLICATE(
            40944,
            "供应商编码已存在"),

    /**
     * 供应商 SKU 关系重复，作为数据库唯一索引的并发兜底映射。
     */
    SUPPLIER_SKU_CONFLICT(
            40946,
            "供应商SKU配置已存在"),

    /**
     * 删除供应商前检测到活动商品关联。
     */
    SUPPLIER_IN_USE(
            40947,
            "该供应商已被商品关联引用，不能删除"),

    /**
     * 新增：默认采购员不存在。
     */
    SUPPLIER_SKU_PURCHASER_INVALID(
            40040,
            "默认采购员不正确");

    private final int code;
    private final String msg;
}
