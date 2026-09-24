package net.lab1024.sa.admin.module.scm.sorting.constant;

import java.util.Set;

/**
 * 分拣任务的固定口径：状态集合、结果集合与权限码。
 *
 * <p>状态只有四个（裁决补充第 16 条）：取消即释放占用，不存在 {@code RELEASED} 任务状态；
 * 明细行上另有 {@code ACTIVE / RELEASED} 占用位，两者不要混用。
 */
public final class SortingConstant {

    public static final String PENDING = "PENDING";
    public static final String SORTING = "SORTING";
    public static final String COMPLETED = "COMPLETED";
    public static final String CANCELLED = "CANCELLED";

    public static final String NORMAL = "NORMAL";

    public static final String OCCUPY_ACTIVE = "ACTIVE";

    /**
     * 还能干活的状态：录入、完成、指派都以此为准；已完成只能走重开，已取消不再改动。
     */
    public static final Set<String> WORKING = Set.of(PENDING, SORTING);

    /**
     * 可出单状态：待分拣还没开始，已取消不再出单，两者都不给打印。
     */
    public static final Set<String> PRINTABLE = Set.of(SORTING, COMPLETED);

    /**
     * 建单与指派权（裁决补充第 15 条）：持者才能创建任务、指派/改派、取消与重开，
     * 并且跨指派人可见；分拣员只看到派给自己的任务。
     */
    public static final String ASSIGN_PERM = "scm:sorting:task:assign";

    public static final String QUERY_PERM = "scm:sorting:task:query";

    public static final String ITEM_UPDATE_PERM = "scm:sorting:item:update";

    /**
     * 单号前缀：SRT + 业务日(Asia/Shanghai) + 全局非重置序号（裁决第 8 条）。
     */
    public static final String TASK_NO_PREFIX = "SRT";

    private SortingConstant() {
    }
}
