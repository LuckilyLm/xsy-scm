package com.xsy.scm.supplier.manager;

import org.apache.commons.lang3.StringUtils;

/**
 * 供应商字段归一化：名称只去首尾空白，可选文本空白视作未填写。 编码由服务端生成，这里不做编码归一化。
 */
public final class SupplierValidator {

    private SupplierValidator() {
    }

    /** 名称归一化：只去首尾空白，保留大小写。 */
    public static String normalizeName(String raw) {
        return raw == null ? null : raw.trim();
    }

    /**
     * 可选文本归一化：去首尾空白，空白返回 {@code null}。
     *
     * <p>
     * 返回 null 而不是空串，{@code FieldStrategy.ALWAYS} 才能真正把列清空。
     */
    public static String normalizeOptional(String raw) {
        return StringUtils.trimToNull(raw);
    }
}
