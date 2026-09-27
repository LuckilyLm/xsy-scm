package com.xsy.scm.sorting.domain.form;

import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;
import com.xsy.scm.common.validation.ScmEnumValue;
import com.xsy.scm.sorting.constant.ScmSortingTaskStatusEnum;
import net.lab1024.sa.base.common.domain.PageParam;

/**
 * 按商品汇总的只读视角：跨订单按 SKU + 单位分组，**不跨单位求和**。
 * 本视角不承载任何录入动作（裁决补充第 19 条）。
 */
@Data
public class SortingSummaryQueryForm extends PageParam {
    @Size(max = 100, message = "搜索关键词不能超过100个字符")
    private String keyword;

    @Positive(message = "仓库 ID 必须大于0")
    private Long warehouseId;

    @ScmEnumValue(enumClass = ScmSortingTaskStatusEnum.class, message = "分拣任务状态无效")
    private String taskStatus;
}
