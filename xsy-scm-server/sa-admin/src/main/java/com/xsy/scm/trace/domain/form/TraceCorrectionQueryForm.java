package com.xsy.scm.trace.domain.form;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;
import net.lab1024.sa.base.common.domain.PageParam;

/**
 * 更正申请列表：按状态、包装码、批次号收窄。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class TraceCorrectionQueryForm extends PageParam {

    @Size(max = 16, message = "状态不能超过16个字符")
    private String status;

    @Size(max = 32, message = "包装码不能超过32个字符")
    private String unitCode;

    @Size(max = 32, message = "批次号不能超过32个字符")
    private String lotNo;

    @Override
    @Min(value = 1, message = "页码必须至少为1")
    public Long getPageNum() {
        return super.getPageNum();
    }

    @Override
    @Min(value = 1, message = "每页条数必须至少为1")
    @Max(value = 100, message = "每页条数不能超过100")
    public Long getPageSize() {
        return super.getPageSize();
    }
}
