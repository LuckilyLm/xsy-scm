package com.xsy.scm.warehouse.domain.vo;

import lombok.Data;

/**
 * 某个仓库下被授权的员工行（仓库授权维护页用）。
 */
@Data
public class WarehouseScopeEmployeeVO {

    private Long employeeId;

    private String loginName;

    private String actualName;

    /** 员工是否已停用。授权行可能早于人员离职，维护页要能看出「这个仓只有离职的人能看」。 */
    private Boolean disabledFlag;
}
