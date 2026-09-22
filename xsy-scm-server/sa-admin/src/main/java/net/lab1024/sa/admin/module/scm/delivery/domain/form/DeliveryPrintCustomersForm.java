package net.lab1024.sa.admin.module.scm.delivery.domain.form;

import lombok.Data;
import jakarta.validation.constraints.*;

import java.util.List;

/**
 * 按客户正式生成打印：{@code customerIds} 选择客户，{@code orderPrintFilter} 决定这些客户中
 * 打印哪些订单（PARTIAL 客户可只补打未打印订单）。
 */
@Data
public class DeliveryPrintCustomersForm {
    @NotNull
    @Min(0)
    private Integer version;
    @NotEmpty
    @Size(max = 500)
    private List<@NotNull @Positive Long> customerIds;
    /**
     * ALL / PRINTED / UNPRINTED，默认 ALL；不接受 PARTIAL（PARTIAL 是客户维度状态，不是订单筛选）。
     */
    @Pattern(regexp = "ALL|PRINTED|UNPRINTED")
    private String orderPrintFilter = "ALL";
}
