package com.xsy.scm.supplier.domain.form;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 整表替换某供应商的商品关联。
 *
 * <p>
 * 这是 {@code supplier_sku} 的<b>唯一</b>写入口，使用整表替换语义。不做行级 add / update / delete 端点，避免两套规则漂移。
 *
 * <p>
 * {@code items} 为空数组表示<b>清空全部关联</b>，不是「无操作」。
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
