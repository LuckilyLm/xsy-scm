package net.lab1024.sa.admin.module.scm.warehouse.domain.form;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;
import net.lab1024.sa.base.common.domain.PageParam;

/**
 * 仓库列表查询条件（W5 Target Design §7.2）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class WarehouseQueryForm extends PageParam {

    @Size(max = 64)
    private String warehouseCode;

    @Size(max = 150)
    private String name;

    @Pattern(regexp = "ENABLED|DISABLED")
    private String status;

    @Override
    @Min(1)
    public Long getPageNum() {
        return super.getPageNum();
    }

    @Override
    @Min(1)
    @Max(100)
    public Long getPageSize() {
        return super.getPageSize();
    }
}
