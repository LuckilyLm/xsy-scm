package net.lab1024.sa.admin.module.scm.supplier.domain.form;

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
 * <p>这是 {@code supplier_sku} 的<b>唯一</b>写入口（保留 legacy R5 的整表替换语义）。
 * 不做行级 add / update / delete 端点，避免两套规则漂移。
 *
 * <p>{@code items} 为空数组表示<b>清空全部关联</b>（legacy 不变量 R11），不是「无操作」。
 */
@Data
public class SupplierSkuReplaceForm {

    @NotNull
    @Positive
    private Long supplierId;

    /** 上限 500：单供应商的关联数量有界，超限返回 40000（Target Design 风险 R2）。 */
    @Valid
    @NotNull
    @Size(max = 500)
    private List<SupplierSkuItemForm> items = new ArrayList<>();
}
