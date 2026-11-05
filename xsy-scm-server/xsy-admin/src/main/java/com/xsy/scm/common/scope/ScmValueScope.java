package com.xsy.scm.common.scope;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 按仓库、业务员、采购员或司机限定数据范围。全部授权用 all()，无授权用 none()，不用 null 顶替。没分配的数据只有全部授权的人能看到。
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

    /** 授权 id 清单；{@code null} 元素被丢弃，空清单按 {@link #none()} 处理。 */
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

    /** MyBatis OGNL 以 {@code scope.ids} 读取 foreach 集合；{@link #isAll()} 为真时用不到。 */
    public Set<Long> getIds() {
        return ids;
    }

    /** 本维度是否必然返回 0 行；调用方据此直接短路，避免把空集合送进 {@code IN ()}。 */
    public boolean isEmpty() {
        return !all && ids.isEmpty();
    }

    /** 单条数据是否落在授权范围内。未分配（{@code null}）的数据只有 {@link #all()} 可见。 */
    public boolean allows(Long id) {
        return all || (id != null && ids.contains(id));
    }

    /** 叠加调用方传入的筛选值：结果只会更小或不变。 */
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
