package net.lab1024.sa.admin.module.scm.common.scope;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 单个维度（仓库 / 客户业务员 / 订单业务员 / 采购员 / 司机）的授权取值范围。
 *
 * <p>只有两个极端形态是显式的：{@link #all()} 表示「本维度不收窄」，{@link #none()} 表示
 * 「本维度无任何授权」。两者都不用 {@code null} 表达，因为 {@code null} 会被调用方读成「全部」，
 * 而本类的默认语义是失败关闭：没有授权就查不到数据
 * （裁决见 {@code docs/decisions.md}「P0 基线收口裁决」第 2、4 条）。
 *
 * <p>{@link #isAll()} 为真时 Mapper 不拼任何本维度谓词，因此 {@code owner_id IS NULL}
 * 的未分配行也照常可见；反之 {@code IN (...)} 天然排除 NULL，
 * 正好对应「未分配数据普通业务员不可见」。
 */
public final class ScmValueScope {

    private static final ScmValueScope ALL = new ScmValueScope(true, Set.of());

    private static final ScmValueScope NONE = new ScmValueScope(false, Set.of());

    private final boolean all;

    private final Set<Long> ids;

    private ScmValueScope(boolean all, Set<Long> ids) {
        this.all = all;
        this.ids = ids;
    }

    public static ScmValueScope all() {
        return ALL;
    }

    public static ScmValueScope none() {
        return NONE;
    }

    /**
     * 授权 id 清单；{@code null} 元素被丢弃，空清单收敛成 {@link #none()} 而不是「全部」。
     */
    public static ScmValueScope of(Collection<Long> authorizedIds) {
        if (authorizedIds == null || authorizedIds.isEmpty()) {
            return NONE;
        }
        Set<Long> copy = new LinkedHashSet<>();
        for (Long id : authorizedIds) {
            if (id != null) {
                copy.add(id);
            }
        }
        return copy.isEmpty() ? NONE : new ScmValueScope(false, Set.copyOf(copy));
    }

    /** MyBatis OGNL 以 {@code scope.all} 读取本方法。 */
    public boolean isAll() {
        return all;
    }

    /** MyBatis OGNL 以 {@code scope.ids} 读取 foreach 集合；{@link #isAll()} 为真时不会被用到。 */
    public Set<Long> getIds() {
        return ids;
    }

    /**
     * 本维度是否必然返回 0 行：调用方据此直接短路，避免把空集合送进 {@code IN ()}。
     */
    public boolean isEmpty() {
        return !all && ids.isEmpty();
    }

    /**
     * 单条数据是否落在授权范围内。{@code null} 归属（未分配）只有 {@link #all()} 才可见。
     */
    public boolean allows(Long id) {
        return all || (id != null && ids.contains(id));
    }

    /**
     * 叠加调用方自己传的筛选值：结果只会等于或更小，不可能更大。
     */
    public ScmValueScope narrow(Long requestedId) {
        if (requestedId == null) {
            return this;
        }
        return allows(requestedId) ? new ScmValueScope(false, Set.of(requestedId)) : NONE;
    }

    @Override
    public String toString() {
        return all ? "ALL" : ids.toString();
    }
}
