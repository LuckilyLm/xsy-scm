package net.lab1024.sa.admin.module.scm.supplier.domain.form;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import lombok.Data;
import lombok.EqualsAndHashCode;
import net.lab1024.sa.base.common.domain.PageParam;

/**
 * 商品-供应商关系分页查询（只读反查视图）。
 *
 * <p>W2 只提供「按 SKU 反查供应商」的读路径；写入只有 {@code /scm/supplier/sku/replace} 一个入口。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class SupplierSkuQueryForm extends PageParam {

    @Positive
    private Long supplierId;

    @Positive
    private Long skuId;

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
