package com.xsy.scm.customer.domain.form;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;
import net.lab1024.sa.base.common.domain.PageParam;
import com.xsy.scm.common.constant.ScmEnableStatusEnum;
import com.xsy.scm.common.validation.ScmEnumValue;

/**
 * 客户类型列表查询条件。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class CustomerTypeQueryForm extends PageParam {

    /**
     * 关键字：类型编码 / 类型名称。
     */
    @Size(max = 100, message = "关键字不能超过100个字符")
    private String keyword;

    @ScmEnumValue(enumClass = ScmEnableStatusEnum.class, message = "客户类型状态无效")
    private String status;

    @Override
    @Min(value = 1, message = "页码必须至少为1")
    public Long getPageNum() {
        return super.getPageNum();
    }

    @Override
    @Min(value = 1, message = "每页条数必须在1至100之间")
    @Max(value = 100, message = "每页条数必须在1至100之间")
    public Long getPageSize() {
        return super.getPageSize();
    }
}
