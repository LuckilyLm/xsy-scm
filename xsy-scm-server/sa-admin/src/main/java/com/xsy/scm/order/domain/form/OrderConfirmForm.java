package com.xsy.scm.order.domain.form;

import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class OrderConfirmForm extends OrderVersionForm {
    private Boolean creditOverride;
    @Size(max = 500, message = "授信例外原因不能超过500个字符")
    private String creditOverrideReason;

    /**
     * 客户选用的券实例；为空表示本单不使用券。
     *
     * <p>
     * 只接受「用哪张券」：券是客户自己的权益，必须由调用方显式指定券实例 id，服务端不自动挑券。
     * <b>活动不由客户端指定</b>——服务端按当前生效活动与互斥组自行选出，避免客户端拼优惠组合。
     */
    @Positive(message = "券实例 ID 必须大于0")
    private Long couponInstanceId;
}
