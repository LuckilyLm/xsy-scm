package com.xsy.scm.delivery.domain.form;

import lombok.Data;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import com.xsy.scm.common.validation.ScmEnumValue;
import com.xsy.scm.delivery.constant.ScmDeliveryCustomerPrintFilterEnum;
import com.xsy.scm.delivery.constant.ScmDeliveryOrderPrintFilterEnum;

import java.util.List;

/**
 * 按客户正式生成打印：两个维度分开，先按客户状态圈定客户，再决定这些客户中打印哪些订单。
 *
 * <p>
 * {@code customerIds} 只是候选范围（缺省表示线路上全部客户），客户是否 PRINTED / UNPRINTED / PARTIAL 一律由服务端在持有线路锁后按当前 ACTIVE
 * 订单重新聚合判定，不接受前端传来的状态。
 */
@Data
public class DeliveryPrintCustomersForm {
    @NotNull(message = "版本号不能为空")
    @Min(value = 0, message = "版本号不能小于0")
    private Integer version;
    @Size(max = 500, message = "客户数量不能超过500个")
    private List<@NotNull(message = "客户编号不能为空") @Positive(message = "客户编号必须为正数") Long> customerIds;
    /**
     * ALL / PRINTED / UNPRINTED / PARTIAL，默认 ALL；缺省 {@code customerIds} 时不允许 ALL，否则一次请求会无选择地重打整条线路。
     */
    @ScmEnumValue(enumClass = ScmDeliveryCustomerPrintFilterEnum.class, message = "客户打印状态筛选值无效")
    private String customerStatusFilter = ScmDeliveryCustomerPrintFilterEnum.ALL.name();
    /**
     * ALL / PRINTED / UNPRINTED，默认 ALL；不接受 PARTIAL（PARTIAL 是客户维度状态，不是订单筛选）。
     */
    @ScmEnumValue(enumClass = ScmDeliveryOrderPrintFilterEnum.class, message = "订单打印状态筛选值无效")
    private String orderPrintFilter = ScmDeliveryOrderPrintFilterEnum.ALL.name();
}
