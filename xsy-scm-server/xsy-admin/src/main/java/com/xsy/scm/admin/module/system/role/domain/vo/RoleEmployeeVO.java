package com.xsy.scm.admin.module.system.role.domain.vo;

import lombok.Data;

/**
 * 角色的员工
 *
 */
@Data
public class RoleEmployeeVO {

    private Long roleId;

    private Long employeeId;

    private String roleName;
}
