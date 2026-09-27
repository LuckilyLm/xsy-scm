package com.xsy.scm.supplier.domain.form;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;
import net.lab1024.sa.base.common.domain.PageParam;
import com.xsy.scm.common.constant.ScmEnableStatusEnum;
import com.xsy.scm.common.validation.ScmEnumValue;

/**
 * 供应商列表查询条件。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class SupplierQueryForm extends PageParam {

    /**
     * 关键字：供应商编码 / 名称 / 联系人 / 联系电话。
     */
    @Size(max = 150, message = "关键字不能超过150个字符")
    private String keyword;

    @ScmEnumValue(enumClass = ScmEnableStatusEnum.class, message = "供应商状态无效")
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
