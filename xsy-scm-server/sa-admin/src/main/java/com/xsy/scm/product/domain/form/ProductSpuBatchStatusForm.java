package com.xsy.scm.product.domain.form;

import com.xsy.scm.common.constant.ScmShelfStatusEnum;
import com.xsy.scm.common.validation.ScmEnumValue;
import com.xsy.scm.product.constant.ScmProductMasterStatusEnum;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 批量上下架（status）与批量主档启停（masterStatus）共用一个入口，至少给一个目标状态。
 */
@Data
public class ProductSpuBatchStatusForm {
    @NotEmpty(message = "批量项目列表不能为空")
    @Size(max = 200, message = "批量项目列表不能超过200项")
    @Valid
    private List<
            ProductBatchItemForm> items;
    @ScmEnumValue(enumClass = ScmShelfStatusEnum.class, message = "商品销售状态无效")
    private String status;
    @ScmEnumValue(enumClass = ScmProductMasterStatusEnum.class, message = "商品主档状态无效")
    private String masterStatus;

    /**
     * 两个目标状态可以只给一个（只改在售 / 只改主档），但全空就是一次无效命令。
     */
    @AssertTrue(message = "status 与 masterStatus 至少提供一个")
    @JsonIgnore
    public boolean isTargetPresent() {
        return status != null || masterStatus != null;
    }
}
