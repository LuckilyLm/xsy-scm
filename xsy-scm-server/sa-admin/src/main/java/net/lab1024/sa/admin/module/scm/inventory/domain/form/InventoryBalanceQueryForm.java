package net.lab1024.sa.admin.module.scm.inventory.domain.form;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;
import net.lab1024.sa.base.common.domain.PageParam;

/**
 * 库存余额列表查询条件（W6 Target Design §10.1）。
 *
 * <p><b>Q12 已裁决：后端不做任何隐式默认</b>。{@code warehouseId} 为空即不按仓库过滤，
 * 返回的是<b>当前调用者被授权的那些仓库</b>的余额行，而不是全库 —— 服务端既不会替你选仓库，
 * 也不会因为你没筛就绕过数据范围。
 * 「恰好只有 1 个启用仓库时默认带出」是**前端**行为，落在余额页加载时。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class InventoryBalanceQueryForm extends PageParam {

    private Long warehouseId;

    private Long skuId;

    /**
     * SKU 编码模糊匹配。
     */
    @Size(max = 64)
    private String skuCode;

    /**
     * 商品名称模糊匹配（{@code product_spu.name}）。
     */
    @Size(max = 150)
    private String productName;

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
