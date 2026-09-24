package net.lab1024.sa.admin.module.scm.delivery.domain.entity;

import lombok.Data;
import lombok.EqualsAndHashCode;
import com.baomidou.mybatisplus.annotation.*;

import java.math.BigDecimal;
import java.time.*;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.NullSerializer;
import net.lab1024.sa.admin.module.scm.common.json.ScmFixedScale4Serializer;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "delivery_driver", autoResultMap = true)
public class DeliveryDriverEntity extends DeliveryRecord {
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String driverCode;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String driverName;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String phone;
    /**
     * 绑定的系统员工 id：把登录人映射回司机档案，是线路数据范围的唯一依据。
     * {@code ALWAYS} 更新策略是解绑（改回 NULL）能落库的前提。
     */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long employeeId;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String status;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String remark;
}
