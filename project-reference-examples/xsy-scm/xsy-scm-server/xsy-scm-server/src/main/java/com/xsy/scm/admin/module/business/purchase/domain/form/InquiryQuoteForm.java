package com.xsy.scm.admin.module.business.purchase.domain.form;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/**
 * 供应商报价 表单
 *
 * @author xsy-scm
 */
@Data
public class InquiryQuoteForm {

    @Schema(description = "询价单ID")
    @NotNull(message = "询价单ID不能为空")
    private Long inquiryId;

    @Schema(description = "供应商ID")
    @NotNull(message = "供应商ID不能为空")
    private Long supplierId;

    @Schema(description = "报价明细")
    @Valid
    @NotEmpty(message = "报价明细不能为空")
    private List<InquiryQuoteItemForm> items;
}
