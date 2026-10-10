package com.xsy.scm.mall.account.domain.form;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class MallAccountStatusForm {
    @NotNull(message = "商城账号不能为空")
    private Long accountId;
    @NotNull(message = "启用状态不能为空")
    private Boolean enabled;
}
