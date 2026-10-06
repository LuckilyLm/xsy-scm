package com.xsy.scm.balance.domain.form;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

/** 余额概览查询。 */
@Data
public class BalanceQueryForm {

    /**
     * 实际业务客户。服务端据此解析<b>结算主体</b>再去取钱包， 不接受客户端直接指定钱包账户 id —— 那会绕开客户与结算主体的关系。
     */
    @NotNull(message = "客户不能为空")
    @Positive(message = "客户 ID 必须大于0")
    private Long customerId;
}
