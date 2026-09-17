package com.xsy.scm.admin.module.business.purchase.domain.form;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 采购收货单 更新表单
 *
 * @author xsy-scm
 */
@Data
public class ReceiveUpdateForm extends ReceiveAddForm {

    @Schema(description = "收货单ID")
    @NotNull(message = "收货单ID不能为空")
    private Long receiveId;
}
