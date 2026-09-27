package com.xsy.scm.warehouse.domain.form;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;
import com.xsy.scm.common.validation.ScmEnumValue;
import com.xsy.scm.warehouse.constant.ScmWarehouseStatusEnum;
import net.lab1024.sa.base.common.domain.PageParam;

/**
 * 仓库列表查询条件。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class WarehouseQueryForm extends PageParam {

    @Size(max = 64)
    private String warehouseCode;

    @Size(max = 150)
    private String name;

    @ScmEnumValue(enumClass = ScmWarehouseStatusEnum.class, message = "仓库状态无效")
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
