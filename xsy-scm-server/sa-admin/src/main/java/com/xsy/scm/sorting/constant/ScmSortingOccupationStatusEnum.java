package com.xsy.scm.sorting.constant;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 订单行被分拣任务占用的状态。
 *
 * <p>
 * {@code desc} 供页面与导出使用，保存仍用枚举名。
 */
@Getter
@RequiredArgsConstructor
public enum ScmSortingOccupationStatusEnum {

    /** 占用中：该订单行已归属一个分拣任务。 */
    ACTIVE("占用中"),

    /** 已释放：任务取消或完成后释放，可重新进入新任务。 */
    RELEASED("已释放");

    private final String desc;
}
