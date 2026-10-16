package com.xsy.scm.common.scope;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 按仓库、业务员、采购员或司机限定数据范围。 全部授权和无授权分别用 all()、none() 明确表示，不用 null 代替。 未分配的数据只有全部授权者可见。
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
