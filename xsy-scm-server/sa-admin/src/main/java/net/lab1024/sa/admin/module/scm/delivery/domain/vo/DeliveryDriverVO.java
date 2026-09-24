package net.lab1024.sa.admin.module.scm.delivery.domain.vo;

import lombok.Data;
import lombok.EqualsAndHashCode;

import net.lab1024.sa.admin.module.scm.delivery.domain.entity.DeliveryDriverEntity;

/**
 * 司机列表行：绑定员工名称只服务展示，范围判定仍用 {@code employeeId} 本身。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class DeliveryDriverVO extends DeliveryDriverEntity {
    /** 绑定员工的姓名；员工已删除时保留名字快照不可得，故为空。 */
    private String employeeName;
}
