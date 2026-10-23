package com.xsy.scm.inventory.domain.form;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;
import net.lab1024.sa.base.common.domain.PageParam;

/**
 * 批次库存查询条件。
 *
 * <p>
 * 与余额查询同一规则：不传仓库就返回当前调用者被授权的那些仓库的批次行，服务端不会因为没筛仓库而绕过数据范围。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class InventoryLotBalanceQueryForm extends PageParam {

    private Long warehouseId;

    private Long skuId;

    @Size(max = 32, message = "批次号不能超过32个字符")
    private String lotNo;

    @Size(max = 64, message = "来源生产批号不能超过64个字符")
    private String sourceLotNo;

    @Size(max = 150, message = "商品名称不能超过150个字符")
    private String productName;

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
