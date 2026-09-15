package net.lab1024.sa.admin.module.scm.supplier.manager;

/**
 * 供应商字段归一化。
 *
 * <p>规则与客户域一致：编码 trim + upper，名称仅 trim，可选文本空白视作未填写。
 * 之所以不复用 {@code CustomerValidator} 的静态方法，是为了让两个域可以独立演进
 * （例如未来供应商编码需要带前缀校验时，不需要动客户域）。
 */
public final class SupplierValidator {

    private SupplierValidator() {
    }

    /** 编码归一化：去空白 + 转大写。 */
    public static String normalizeCode(String raw) {
        return raw == null ? null : raw.trim().toUpperCase();
    }

    /** 名称归一化：仅去首尾空白，保留大小写。 */
    public static String normalizeName(String raw) {
        return raw == null ? null : raw.trim();
    }

    /**
     * 可选文本归一化：去首尾空白，空白返回 {@code null}。
     *
     * <p>返回 {@code null} 而不是空串，是为了配合 {@code FieldStrategy.ALWAYS} 真正把列清空；
     * 返回空串会让「清空备注」变成「备注为 ''」，两种形态在搜索与展示上表现不一致。
     */
    public static String normalizeOptional(String raw) {
        if (raw == null) {
            return null;
        }
        String trimmed = raw.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
