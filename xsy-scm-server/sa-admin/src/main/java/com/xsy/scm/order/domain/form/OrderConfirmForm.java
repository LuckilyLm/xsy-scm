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
     * 本次确认显式改选的券实例；为空时商城订单沿用下单时的预选券。
     *
     * <p>
     * 只决定用哪张券，服务端不自动挑券；活动由服务端按当前生效活动与互斥组选出，客户端不能指定。
     */
    @Positive(message = "券实例 ID 必须大于0")
    private Long couponInstanceId;

    /**
     * 操作员显式放弃商城订单的预选券；与 {@code couponInstanceId} 不能同时使用。
     */
    private Boolean clearMallCoupon;
}
