package com.xsy.scm.sorting.constant;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 分拣任务状态，与 {@code sorting_task.status} 的 CHECK 约束逐值对应。
 *
 * <p>
 * {@code desc} 供页面、导出与打印使用，落库仍用枚举名。
 */
@Getter
@RequiredArgsConstructor
public enum ScmSortingTaskStatusEnum {

    /**
     * 待分拣：任务已建，尚未开始录入。
     */
    PENDING("待分拣"),

    /**
     * 分拣中：至少一条明细已录入结果。
     */
    SORTING("分拣中"),

    /**
     * 已完成：任务内全部活动明细都已有结果。
     */
    COMPLETED("已完成"),

    /**
     * 已取消：释放占用，不再作业。
     */
    CANCELLED("已取消");

    private final String desc;
}
