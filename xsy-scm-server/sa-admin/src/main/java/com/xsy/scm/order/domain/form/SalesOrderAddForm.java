package com.xsy.scm.order.domain.form;

import lombok.Data;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.time.OffsetDateTime;

import com.xsy.scm.common.validation.ScmEnumValue;
import com.xsy.scm.order.constant.ScmOrderSourceEnum;

@Data
public class SalesOrderAddForm {
    @NotNull(message = "客户不能为空")
    private Long customerId;
    @NotBlank(message = "订单来源不能为空")
    @ScmEnumValue(enumClass = ScmOrderSourceEnum.class, message = "订单来源无效")
    private String orderSource;
    private Long originalOrderId;
    @Size(max = 500, message = "补单原因不能超过500个字符")
    private String supplementReason;
    @Size(max = 500, message = "订单备注不能超过500个字符")
    private String remark;
    private OffsetDateTime expectDeliveryTime;
    @Valid
    @NotNull(message = "收货地址信息不能为空")
    private OrderAddressForm address;
    @Valid
    @NotEmpty(message = "订单明细不能为空")
    @Size(max = 500, message = "订单明细不能超过500项")
    private List<SalesOrderItemForm> items;
    /**
     * 商城客户预选的券实例；仅 {@code orderSource = MALL} 的建单路径持久化， 后台录单忽略该字段（后台用券在确认命令上显式给出）。
     */
    private Long mallCouponInstanceId;

    /**
     * 商城客户在结算页选定的结算方式（WALLET / CREDIT / COD）。仅商城来源的订单会带；后台建单不填。
     */
    private String settlementMethod;
}
