package com.xsy.scm.inventory.domain.form;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;
import net.lab1024.sa.base.common.domain.PageParam;

/**
 * 库存余额列表查询条件。
 *
 * <p>
 * 后端不为 {@code warehouseId} 设置隐式默认值；为空时不按仓库过滤，返回的是<b>当前调用者被授权的那些仓库</b>的余额行，而不是全库 —— 服务端既不会替你选仓库，也不会因为你没筛就绕过数据范围。 「恰好只有 1
 * 个启用仓库时默认带出」是<b>前端</b>行为，落在余额页加载时。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class InventoryBalanceQueryForm extends PageParam {

    private Long warehouseId;

    private Long skuId;

    /**
     * SKU 编码模糊匹配。
     */
    @Size(max = 64, message = "商品规格编码不能超过64个字符")
    private String skuCode;

    /**
     * 商品名称模糊匹配（{@code product_spu.name}）。
     */
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
