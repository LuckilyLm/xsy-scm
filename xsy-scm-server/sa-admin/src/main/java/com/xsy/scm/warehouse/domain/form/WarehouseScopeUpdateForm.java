package com.xsy.scm.warehouse.domain.form;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 设置某员工可访问的仓库集合，整体替换而不是增量追加。
 *
 * <p>
 * 增量接口没法回收一次错误的授权，而能回收正是这套机制的意义。
 */
@Data
public class WarehouseScopeUpdateForm {

    @NotNull(message = "员工不能为空")
    private Long employeeId;

    /** 目标仓库 id；空清单即回收该员工的全部仓库授权。 */
    @NotNull(message = "授权仓库清单不能为空，清空请传空数组")
    @Size(max = 200, message = "单个员工最多授权 200 个仓库")
    private List<Long> warehouseIds;
}
