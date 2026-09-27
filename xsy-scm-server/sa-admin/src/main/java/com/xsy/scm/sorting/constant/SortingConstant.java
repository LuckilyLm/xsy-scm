package com.xsy.scm.sorting.constant;

import java.util.Set;

/**
 * 分拣任务的固定口径：工作状态集合、可打印状态集合与单号前缀。
 *
 * <p>
 * 状态只有四个：取消即释放占用，不存在 {@code RELEASED} 任务状态； 明细行上另有 {@code ACTIVE / RELEASED} 占用位，两者不要混用。
 */
public final class SortingConstant {

    /**
     * 还能干活的状态：录入、完成、指派都以此为准；已完成只能走重开，已取消不再改动。
     */
    public static final Set<String> WORKING = Set.of(ScmSortingTaskStatusEnum.PENDING.name(),
            ScmSortingTaskStatusEnum.SORTING.name());

    /**
     * 可出单状态：待分拣还没开始，已取消不再出单，两者都不给打印。
     */
    public static final Set<String> PRINTABLE = Set.of(ScmSortingTaskStatusEnum.SORTING.name(),
            ScmSortingTaskStatusEnum.COMPLETED.name());

    /**
     * 单号前缀：SRT + 业务日(Asia/Shanghai) + 全局非重置序号。
     */
    public static final String TASK_NO_PREFIX = "SRT";

    private SortingConstant() {
    }
}
