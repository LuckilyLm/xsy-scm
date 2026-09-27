package com.xsy.scm.pricing.domain.form;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class AgreementPriceUpdateForm extends AgreementPriceAddForm {
    @NotNull(message = "协议价 ID 不能为空")
    private Long agreementPriceId;
    @NotNull(message = "版本号不能为空")
    @Min(value = 0, message = "版本号不能小于0")
    private Integer version;
}
