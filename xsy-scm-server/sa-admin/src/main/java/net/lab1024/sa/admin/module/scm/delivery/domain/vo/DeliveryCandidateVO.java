package net.lab1024.sa.admin.module.scm.delivery.domain.vo;
import lombok.Data;
import java.util.List;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import net.lab1024.sa.admin.module.scm.delivery.domain.entity.*;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import net.lab1024.sa.admin.module.scm.common.json.ScmFixedScale4Serializer;
@Data
public class DeliveryCandidateVO extends net.lab1024.sa.admin.module.scm.order.domain.vo.OrderAddressSnapshotVO {
    private String orderNo;
    private String customerName;
    private String status;
    private Integer itemCount;
    private OffsetDateTime expectDeliveryTime;
    @JsonSerialize(using=ScmFixedScale4Serializer.class,nullsUsing=ScmFixedScale4Serializer.class)
    private BigDecimal orderAmount;
}
