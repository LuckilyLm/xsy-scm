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
@TableName(value="delivery_driver",autoResultMap=true)
public class DeliveryDriverEntity extends DeliveryRecord {
    @TableField(updateStrategy=FieldStrategy.ALWAYS) private String driverCode;
    @TableField(updateStrategy=FieldStrategy.ALWAYS) private String driverName;
    @TableField(updateStrategy=FieldStrategy.ALWAYS) private String phone;
    @TableField(updateStrategy=FieldStrategy.ALWAYS) private String status;
    @TableField(updateStrategy=FieldStrategy.ALWAYS) private String remark;
}
