package net.lab1024.sa.admin.module.scm.supplier.domain.form;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;
import net.lab1024.sa.admin.module.scm.common.json.ScmStrictDecimalStringDeserializer;
import net.lab1024.sa.admin.module.scm.common.util.ScmDecimalStrings;

/**
 * 关联表中的一行。
 *
 * <p>{@code id} 与 {@code version} 的存在与否决定这一行的语义（legacy 不变量 R7–R10）：
 * <ul>
 *   <li>带 {@code id}：更新既有行，{@code version} 必须与库中一致，且 {@code skuId} 不得变更；</li>
 *   <li>不带 {@code id}：新增行；若 {@code (supplierId, skuId)} 已存在则复用该行而不是报错；</li>
 *   <li>库中存在但请求里没有的行 → 软删。</li>
 * </ul>
 *
 * <p><b>刻意允许同一供应商多条 {@code defaultFlag = true}</b>（R12）——不在这里加任何互斥校验。
 */
@Data
public class SupplierSkuItemForm {

    /** 既有行主键；新增行为 {@code null}。 */
    @Positive
    private Long id;

    /** 既有行版本号；新增行为 {@code null}。 */
    @Min(0)
    private Integer version;

    @NotNull
    @Positive
    private Long skuId;

    @NotBlank
    @Size(max = 32)
    private String purchaseUnit;

    /** 参考价，4 位定点字符串；可空。 */
    @Pattern(regexp = ScmDecimalStrings.PATTERN)
    @JsonDeserialize(using = ScmStrictDecimalStringDeserializer.class)
    private String referencePrice;

    /** 默认采购员，引用 SmartAdmin {@code t_employee.employee_id}。 */
    @Positive
    private Long purchaserId;

    @NotNull
    private Boolean defaultFlag = false;

    /** 缺省为 {@code ENABLED}（R20）。 */
    @Pattern(regexp = "ENABLED|DISABLED")
    private String status;
}
