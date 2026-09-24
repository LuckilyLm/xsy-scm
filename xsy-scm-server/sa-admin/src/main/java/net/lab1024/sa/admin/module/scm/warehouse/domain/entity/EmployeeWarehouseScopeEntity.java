package net.lab1024.sa.admin.module.scm.warehouse.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 员工被授权的仓库（SCM 数据范围仓库维度的唯一事实来源）。
 *
 * <p>一行 = 一人一仓。角色只回答「能做什么」，本表回答「能看哪些仓的数据」，
 * 因此不把仓库编进角色名、也不借用部门（口径见 {@code docs/decisions.md}
 * 「P0 基线收口裁决」第 1、8 条）。没有任何隐式默认：无活动行即看不到该仓数据。
 *
 * <p>列名沿用 SmartAdmin 底座风格（{@code deleted_flag} / {@code create_time}），
 * 与 SCM 业务表的 {@code deleted} / {@code created_at} 不同，映射必须逐列写清。
 */
@Data
@TableName("employee_warehouse_scope")
public class EmployeeWarehouseScopeEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 员工 id，对应 {@code t_employee.employee_id}；按项目约定不建数据库外键，存在性在服务层校验。
     */
    private Long employeeId;

    /**
     * 仓库 id，对应 {@code warehouse.id}；同样无外键。
     */
    private Long warehouseId;

    private Boolean deletedFlag;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
