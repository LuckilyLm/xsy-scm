package com.xsy.scm.mall.order.domain.form;

import com.xsy.scm.common.validation.ScmEnumValue;
import com.xsy.scm.mall.order.constant.MallOrderDisplayStatusEnum;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class MallOrderQueryForm {
    @NotNull(message = "页码不能为空")
    @Min(value = 1, message = "页码必须大于0")
    private Long pageNum;

    @NotNull(message = "每页数量不能为空")
    @Min(value = 1, message = "每页数量必须大于0")
    @Max(value = 100, message = "每页最多100条")
    private Long pageSize;

    /**
     * 展示分类筛选；留空表示「全部」。取值与 {@link MallOrderDisplayStatusEnum} 一致， 只筛展示分类，不改变订单状态。
     */
    @ScmEnumValue(enumClass = MallOrderDisplayStatusEnum.class, message = "订单分类无效")
    private String displayStatus;
}
