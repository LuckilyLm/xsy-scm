package com.xsy.scm.supplier.manager;

import org.apache.commons.lang3.StringUtils;

/**
 * 供应商字段归一化：名称仅 trim，可选文本空白视作未填写。
 *
 * <p>
 * 这里没有编码归一化 —— 供应商编码由服务端生成，客户端不再提交它（客户类型编码仍由人工录入，归一化留在客户域）。
 */
public final class SupplierValidator {

    private SupplierValidator() {
    }

    /**
     * 名称归一化：仅去首尾空白，保留大小写。
     */
    public static String normalizeName(String raw) {
        return raw == null ? null : raw.trim();
    }

    /**
     * 可选文本归一化：去首尾空白，空白返回 {@code null}。
     *
     * <p>
     * 返回 {@code null} 而不是空串，是为了配合 {@code FieldStrategy.ALWAYS} 真正把列清空；返回空串会让「清空备注」变成「备注为 ''」，两种形态在搜索与展示上表现不一致。
     */
    public static String normalizeOptional(String raw) {
        return StringUtils.trimToNull(raw);
    }
}
