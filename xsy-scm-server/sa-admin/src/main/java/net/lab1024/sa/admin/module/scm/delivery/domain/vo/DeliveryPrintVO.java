package net.lab1024.sa.admin.module.scm.delivery.domain.vo;
import lombok.Data;
import java.util.List;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import net.lab1024.sa.admin.module.scm.delivery.domain.entity.*;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import net.lab1024.sa.admin.module.scm.common.json.ScmFixedScale4Serializer;
@Data
public class DeliveryPrintVO {
    private DeliveryDetailVO detail;
    private List<net.lab1024.sa.admin.module.scm.order.domain.entity.SalesOrderItemEntity> items;
}
