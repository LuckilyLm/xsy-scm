package com.xsy.scm.supplier.domain.form;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 维护商品关联时提交完整列表；传空列表表示清除该供应商全部关联，不是跳过保存。
 */
@Data
public class SupplierSkuReplaceForm {

    @NotNull(message = "供应商 ID 不能为空")
    @Positive(message = "供应商 ID 必须大于0")
    private Long supplierId;

    /**
     * 上限 500：限制单个替换命令的规模。
     */
    @Valid
    @NotNull(message = "供应商商品清单不能为空")
    @Size(max = 500, message = "供应商商品清单不能超过500项")
    private List<SupplierSkuItemForm> items = new ArrayList<>();
}
