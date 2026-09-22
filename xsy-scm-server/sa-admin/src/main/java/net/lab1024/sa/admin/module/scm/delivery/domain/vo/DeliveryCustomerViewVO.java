package net.lab1024.sa.admin.module.scm.delivery.domain.vo;

import lombok.Data;

import java.math.BigDecimal;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import net.lab1024.sa.admin.module.scm.common.json.ScmFixedScale4Serializer;

/**
 * 线路「按客户」视角的一行：仅聚合当前线路内有效关联订单，移出 / 取消释放的历史关系不计入。
 */
@Data
public class DeliveryCustomerViewVO {
    private Long customerId;
    private String customerName;
    private Integer orderCount;
    private Integer itemCount;
    @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
    private BigDecimal totalAmount;
    /**
     * 有效订单中已生成过打印（{@code print_count > 0}）的张数，供上层判定 PARTIAL。
     */
    private Integer printedOrderCount;
    /**
     * PRINTED / UNPRINTED / PARTIAL，由打印计数在 Java 侧判定。
     */
    private String printStatus;
}
