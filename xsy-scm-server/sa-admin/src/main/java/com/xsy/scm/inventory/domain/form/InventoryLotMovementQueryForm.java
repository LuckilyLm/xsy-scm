package com.xsy.scm.inventory.domain.form;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;
import net.lab1024.sa.base.common.domain.PageParam;

/** 批次流水查询条件：按仓库、商品规格、批次号与流水类型收窄，数据范围与余额同源。 */
@Data
@EqualsAndHashCode(callSuper = true)
public class InventoryLotMovementQueryForm extends PageParam {

    private Long warehouseId;

    private Long skuId;

    @Size(max = 32, message = "批次号不能超过32个字符")
    private String lotNo;

    @Size(max = 32, message = "流水类型不能超过32个字符")
    private String movementType;

    @Override
    @Min(value = 1, message = "页码必须至少为1")
    public Long getPageNum() {
        return super.getPageNum();
    }

    @Override
    @Min(value = 1, message = "每页条数必须至少为1")
    @Max(value = 100, message = "每页条数不能超过100")
    public Long getPageSize() {
        return super.getPageSize();
    }
}
