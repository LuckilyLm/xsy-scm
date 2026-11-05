package com.xsy.scm.balance.domain.form;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

/**
 * 余额概览查询。
 */
@Data
public class BalanceQueryForm {

    /** 实际业务客户。服务端据此解析结算主体再取余额账户，不接受客户端指定余额账户 id。 */
    @NotNull(message = "客户不能为空")
    @Positive(message = "客户 ID 必须大于0")
    private Long customerId;
}
