package com.xsy.scm.supplier.domain.form;

import com.xsy.scm.common.domain.ScmPartyForm;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 新增供应商时，编码和启用状态由服务端生成；经纬度与坐标系必须成组填写。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class SupplierAddForm extends ScmPartyForm {

    @NotBlank(message = "供应商名称不能为空")
    @Size(max = 150, message = "供应商名称不能超过150个字符")
    private String name;

    @Positive(message = "供应商分类必须大于0")
    private Long businessCategoryId;

    @jakarta.validation.constraints.Min(value = 0, message = "付款账期不能小于0天")
    @jakarta.validation.constraints.Max(value = 3650, message = "付款账期不能超过3650天")
    private Integer paymentPeriodDays = 0;
}
