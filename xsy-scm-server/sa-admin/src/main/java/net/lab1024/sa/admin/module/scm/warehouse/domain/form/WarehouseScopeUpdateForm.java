package net.lab1024.sa.admin.module.scm.warehouse.domain.form;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 设置某员工可访问的仓库集合（整体替换，不是增量追加）。
 *
 * <p>语义必须是「替换」：增量追加的接口没有任何办法回收一次错误的授权，
 * 而回收恰恰是这套机制存在的意义（失败关闭）。
 */
@Data
public class WarehouseScopeUpdateForm {

    @NotNull(message = "员工不能为空")
    private Long employeeId;

    /**
     * 目标授权仓库 id；空清单即回收该员工的全部仓库授权。
     */
    @NotNull(message = "授权仓库清单不能为空，清空请传空数组")
    @Size(max = 200, message = "单个员工最多授权 200 个仓库")
    private List<Long> warehouseIds;
}
