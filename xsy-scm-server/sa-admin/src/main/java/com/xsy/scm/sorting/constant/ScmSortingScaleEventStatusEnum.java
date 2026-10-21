package com.xsy.scm.sorting.constant;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 电子秤读数事件的生命周期，对应 {@code xsy_sorting_scale_event.status}。
 *
 * <p>
 * {@code desc} 供页面与导出使用，落库仍用枚举名。
 */
@Getter
@RequiredArgsConstructor
public enum ScmSortingScaleEventStatusEnum {

    /** 待处理：设备已上报，等待人工接受或驳回。 */
    PENDING("待处理"),

    /** 已接受：读数已写入正式分拣量。 */
    ACCEPTED("已接受"),

    /** 已驳回：不写分拣结果，保留原因供追溯。 */
    REJECTED("已驳回");

    private final String desc;
}
