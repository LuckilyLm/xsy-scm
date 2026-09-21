package net.lab1024.sa.admin.module.scm.delivery.domain.entity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import com.baomidou.mybatisplus.annotation.*;
import java.math.BigDecimal;
import java.time.*;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.NullSerializer;
import net.lab1024.sa.admin.module.scm.common.json.ScmFixedScale4Serializer;
@Data @EqualsAndHashCode(callSuper=true)
@TableName(value="delivery_route_stop",autoResultMap=true)
public class DeliveryRouteStopEntity extends DeliveryRecord {
    @TableField(updateStrategy=FieldStrategy.ALWAYS) private Long routeId;
    @TableField(updateStrategy=FieldStrategy.ALWAYS) private Integer stopSeq;
    @TableField(updateStrategy=FieldStrategy.ALWAYS) private Long customerId;
    @TableField(updateStrategy=FieldStrategy.ALWAYS) private String customerNameSnapshot;
    @TableField(updateStrategy=FieldStrategy.ALWAYS) private String receiverNameSnapshot;
    @TableField(updateStrategy=FieldStrategy.ALWAYS) private String receiverPhoneSnapshot;
    @TableField(updateStrategy=FieldStrategy.ALWAYS) private String addressSnapshot;
    @TableField(updateStrategy=FieldStrategy.ALWAYS) private Integer provinceCode;
    @TableField(updateStrategy=FieldStrategy.ALWAYS) private String provinceName;
    @TableField(updateStrategy=FieldStrategy.ALWAYS) private Integer cityCode;
    @TableField(updateStrategy=FieldStrategy.ALWAYS) private String cityName;
    @TableField(updateStrategy=FieldStrategy.ALWAYS) private Integer districtCode;
    @TableField(updateStrategy=FieldStrategy.ALWAYS) private String districtName;
    @JsonSerialize(nullsUsing=NullSerializer.class)
    @TableField(updateStrategy=FieldStrategy.ALWAYS) private BigDecimal longitude;
    @JsonSerialize(nullsUsing=NullSerializer.class)
    @TableField(updateStrategy=FieldStrategy.ALWAYS) private BigDecimal latitude;
    @TableField(updateStrategy=FieldStrategy.ALWAYS) private String geomCrs;
    @TableField(updateStrategy=FieldStrategy.ALWAYS) private OffsetDateTime plannedArrivalTime;
    @TableField(updateStrategy=FieldStrategy.ALWAYS) private String remark;
}
