package net.lab1024.sa.admin.module.scm.order.domain.entity;

import lombok.Data;
import com.baomidou.mybatisplus.annotation.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Map;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import net.lab1024.sa.admin.module.scm.common.json.ScmFixedScale4Serializer;
import net.lab1024.sa.admin.module.scm.order.support.OrderJsonbTypeHandler;

@Data @TableName(value="order_address_snapshot",autoResultMap=true)
public class OrderAddressSnapshotEntity {
    private Integer provinceCode;
    private String provinceName;
    private Integer cityCode;
    private String cityName;
    private Integer districtCode;
    private String districtName;
    @com.fasterxml.jackson.databind.annotation.JsonSerialize(nullsUsing=com.fasterxml.jackson.databind.ser.std.NullSerializer.class)
    private java.math.BigDecimal longitude;
    @com.fasterxml.jackson.databind.annotation.JsonSerialize(nullsUsing=com.fasterxml.jackson.databind.ser.std.NullSerializer.class)
    private java.math.BigDecimal latitude;
    private String geomCrs;

    @TableId(type=IdType.AUTO) private Long id;
    @TableField(updateStrategy=FieldStrategy.ALWAYS) private Long orderId;
    @TableField(updateStrategy=FieldStrategy.ALWAYS) private Long customerId;
    @TableField(updateStrategy=FieldStrategy.ALWAYS) private String receiverName;
    @TableField(updateStrategy=FieldStrategy.ALWAYS) private String receiverPhone;
    @TableField(updateStrategy=FieldStrategy.ALWAYS) private String address;
    @TableField(updateStrategy=FieldStrategy.ALWAYS) private OffsetDateTime createdAt;
    @TableField(updateStrategy=FieldStrategy.ALWAYS) private String createdBy;
}
