package com.xsy.scm.supplier.domain.form;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import lombok.Data;
import lombok.EqualsAndHashCode;
import net.lab1024.sa.base.common.domain.PageParam;
import com.xsy.scm.common.constant.ScmEnableStatusEnum;
import com.xsy.scm.common.validation.ScmEnumValue;

/**
 * 商品-供应商关系分页查询（只读反查视图）。
 *
 * <p> 只提供「按 SKU 反查供应商」的读路径；写入只有 {@code /scm/supplier/sku/replace} 一个入口。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class SupplierSkuQueryForm extends PageParam {

    @Positive(message = "供应商 ID 必须大于0")
    private Long supplierId;

    @Positive(message = "SKU ID 必须大于0")
    private Long skuId;

    @ScmEnumValue(enumClass = ScmEnableStatusEnum.class, message = "供应商商品状态无效")
    private String status;

    @Override
    @Min(value = 1, message = "页码必须至少为1")
    public Long getPageNum() {
        return super.getPageNum();
    }

    @Override
    @Min(value = 1, message = "每页条数必须在1至100之间")
    @Max(value = 100, message = "每页条数必须在1至100之间")
    public Long getPageSize() {
        return super.getPageSize();
    }
}
