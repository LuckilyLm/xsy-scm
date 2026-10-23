package com.xsy.scm.warehouse.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 每条记录表示一名员工对一个仓库的授权；角色定义功能权限，本表定义仓库数据范围。
 */
@Data
@TableName("xsy_employee_warehouse_scope")
public class EmployeeWarehouseScopeEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 员工 id，对应 {@code t_employee.employee_id}；按项目约定不建数据库外键，存在性在服务层校验。
     */
    private Long employeeId;

    /**
     * 仓库 id，对应 {@code xsy_warehouse.id}；同样无外键。
     */
    private Long warehouseId;

    private Boolean deletedFlag;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
