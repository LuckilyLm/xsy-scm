package net.lab1024.sa.admin.module.scm.delivery.domain.form;

import lombok.Data;
import jakarta.validation.constraints.*;

import java.util.List;

/**
 * 按客户正式生成打印：两个维度分开，先按客户状态圈定客户，再决定这些客户中打印哪些订单。
 *
 * <p>{@code customerIds} 只是候选范围（缺省表示线路上全部客户），客户是否 PRINTED / UNPRINTED /
 * PARTIAL 一律由服务端在持有线路锁后按当前 ACTIVE 订单重新聚合判定，不接受前端传来的状态。
 */
@Data
public class DeliveryPrintCustomersForm {
    @NotNull
    @Min(0)
    private Integer version;
    @Size(max = 500)
    private List<@NotNull @Positive Long> customerIds;
    /**
     * ALL / PRINTED / UNPRINTED / PARTIAL，默认 ALL；缺省 {@code customerIds} 时不允许 ALL，
     * 否则一次请求会无选择地重打整条线路。
     */
    @Pattern(regexp = "ALL|PRINTED|UNPRINTED|PARTIAL")
    private String customerStatusFilter = "ALL";
    /**
     * ALL / PRINTED / UNPRINTED，默认 ALL；不接受 PARTIAL（PARTIAL 是客户维度状态，不是订单筛选）。
     */
    @Pattern(regexp = "ALL|PRINTED|UNPRINTED")
    private String orderPrintFilter = "ALL";
}
