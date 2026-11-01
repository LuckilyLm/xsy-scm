package com.xsy.scm.product.domain.form;

import com.xsy.scm.common.constant.ScmShelfStatusEnum;
import com.xsy.scm.common.validation.ScmEnumValue;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 批量上下架入口，必须给出目标状态。
 */
@Data
public class ProductSpuBatchStatusForm {
    @NotEmpty(message = "批量项目列表不能为空")
    @Size(max = 200, message = "批量项目列表不能超过200项")
    @Valid
    private List<ProductBatchItemForm> items;
    @ScmEnumValue(enumClass = ScmShelfStatusEnum.class, message = "商品销售状态无效")
    private String status;

    /**
     * 批量上下架必须给出目标状态。
     */
    @AssertTrue(message = "请选择商品销售状态")
    @JsonIgnore
    public boolean isTargetPresent() {
        return status != null;
    }
}
