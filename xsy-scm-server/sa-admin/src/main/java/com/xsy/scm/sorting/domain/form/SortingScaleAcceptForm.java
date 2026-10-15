package com.xsy.scm.sorting.domain.form;

import com.xsy.scm.common.validation.ScmEnumValue;
import com.xsy.scm.sorting.constant.ScmSortingResultEnum;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 秤读数人工确认。非标品由操作员选择分拣结果并说明差异；
 * 数量始终取不可编辑的设备原始读数，禁止在确认请求中覆盖。
 */
@Data
public class SortingScaleAcceptForm {

    @NotNull(message = "版本号不能为空")
    @Min(value = 0, message = "版本号不能小于0")
    private Integer version;

    @ScmEnumValue(enumClass = ScmSortingResultEnum.class, message = "分拣结果无效")
    private String result;

    @Size(max = 500, message = "差异原因不能超过500个字符")
    private String reason;
}
