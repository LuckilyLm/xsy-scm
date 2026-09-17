package com.xsy.scm.admin.module.business.customer.domain.form;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 业务员推广二维码 更新表单
 *
 * @author xsy-scm
 */
@Data
public class CustomerQrcodeUpdateForm extends CustomerQrcodeAddForm {

    @Schema(description = "二维码ID")
    @NotNull(message = "二维码ID不能为空")
    private Long qrcodeId;
}
