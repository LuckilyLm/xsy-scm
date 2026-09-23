package net.lab1024.sa.admin.module.scm.delivery.domain.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import net.lab1024.sa.admin.module.scm.common.json.ScmFixedScale4Serializer;

/**
 * 一次正式生成打印的结果：明确列出本次实际包含的订单，避免「打印一批却标记另一批」。
 */
@Data
public class DeliveryPrintResultVO {
    private Long routeId;
    private OffsetDateTime generatedAt;
    private Integer orderCount;
    @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
    private BigDecimal totalAmount;
    private List<DeliveryOrderViewVO> orders;
}
