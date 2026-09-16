package net.lab1024.sa.admin.module.scm.order.domain.vo;

import lombok.Data;
import com.baomidou.mybatisplus.annotation.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Map;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import net.lab1024.sa.admin.module.scm.common.json.ScmFixedScale4Serializer;
import net.lab1024.sa.admin.module.scm.order.support.OrderJsonbTypeHandler;

@Data
public class OrderAddressSnapshotVO {
    private Long id;
    private Long orderId;
    private Long customerId;
    private String receiverName;
    private String receiverPhone;
    private String address;
    private OffsetDateTime createdAt;
    private String createdBy;
}
