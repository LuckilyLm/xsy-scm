package com.xsy.scm.admin.module.business.purchase.domain.form;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 询价单 添加表单
 *
 * @author xsy-scm
 */
@Data
public class InquiryAddForm {

    @Schema(description = "询价单名称")
    private String inquiryName;

    @Schema(description = "询价有效开始时间")
    @NotNull(message = "询价有效开始时间不能为空")
    private LocalDateTime validStart;

    @Schema(description = "询价有效结束时间")
    @NotNull(message = "询价有效结束时间不能为空")
    private LocalDateTime validEnd;

    @Schema(description = "询价明细")
    @Valid
    @NotEmpty(message = "询价明细不能为空")
    private List<InquiryItemForm> items;
}
