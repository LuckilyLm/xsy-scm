package net.lab1024.sa.admin.module.scm.delivery.domain.vo;
import lombok.Data;
import java.util.List;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import net.lab1024.sa.admin.module.scm.delivery.domain.entity.*;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import net.lab1024.sa.admin.module.scm.common.json.ScmFixedScale4Serializer;
@Data
public class DeliveryStopVO extends DeliveryRouteStopEntity {
    private Integer orderCount;
    @JsonSerialize(using=ScmFixedScale4Serializer.class,nullsUsing=ScmFixedScale4Serializer.class)
    private BigDecimal totalAmount;
}
