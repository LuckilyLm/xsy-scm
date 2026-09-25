package net.lab1024.sa.admin.module.scm.delivery.domain.vo;

import lombok.Data;

import java.util.List;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

import net.lab1024.sa.admin.module.scm.delivery.domain.entity.*;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import net.lab1024.sa.admin.module.scm.common.json.ScmFixedScale4Serializer;

@Data
public class DeliveryRouteVO extends DeliveryRouteEntity {
    private Integer stopCount;
    private Integer orderCount;
    private Integer locatedCount;
    @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
    private BigDecimal totalAmount;
    /**
     * 发车产生的出库单号；未发车或整条线路零实发（全缺）时为 null。
     */
    private String outboundNo;
}
