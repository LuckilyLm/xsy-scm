package net.lab1024.sa.admin.module.scm.warehouse.domain.vo;

import lombok.Data;

/**
 * 某个员工被授权的仓库行（仓库授权维护页用）。
 */
@Data
public class WarehouseScopeWarehouseVO {

    private Long warehouseId;

    private String warehouseCode;

    private String warehouseName;

    /**
     * 仓库当前启停状态：授权到已停用仓库不会立刻出问题，但维护页应当看得见。
     */
    private String warehouseStatus;
}
