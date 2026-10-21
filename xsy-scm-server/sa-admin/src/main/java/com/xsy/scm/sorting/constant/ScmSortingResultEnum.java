package com.xsy.scm.sorting.constant;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 分拣任务明细的结果，与数据库 CHECK 约束逐值对应。
 *
 * <p>
 * {@code desc} 供导出与页面展示使用，落库仍用枚举名。
 */
@Getter
@RequiredArgsConstructor
public enum ScmSortingResultEnum {

    /** 与计划量一致。 */
    NORMAL("正常"),

    /** 实拣少于计划。 */
    SHORT("短少"),

    /** 整行缺货。 */
    OUT_OF_STOCK("缺货"),

    /** 实拣多于计划。 */
    OVER("超出");

    private final String desc;
}
