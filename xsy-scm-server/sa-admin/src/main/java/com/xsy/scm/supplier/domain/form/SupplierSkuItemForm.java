package com.xsy.scm.supplier.domain.form;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;
import com.xsy.scm.common.json.ScmStrictDecimalStringDeserializer;
import com.xsy.scm.common.util.ScmDecimalStrings;
import com.xsy.scm.common.constant.ScmEnableStatusEnum;
import com.xsy.scm.common.validation.ScmEnumValue;

/**
 * 关联表中的一行。
 *
 * <p>
 * {@code id} 与 {@code version} 的存在与否决定这一行的语义：
 * <ul>
 * <li>带 {@code id}：更新既有行，{@code version} 必须与库中一致，且 {@code skuId} 不得变更；</li>
 * <li>不带 {@code id}：新增行；若 {@code (supplierId, skuId)} 已存在则复用该行而不是报错；</li>
 * <li>库中存在但请求里没有的行 → 软删。</li>
 * </ul>
 *
 * <p>
 * <b>刻意允许同一供应商多条 {@code defaultFlag = true}</b>，不做互斥校验。
 */
@Data
public class SupplierSkuItemForm {

    /**
     * 既有行主键；新增行为 {@code null}。
     */
    @Positive(message = "供应商商品关系 ID 必须大于0")
    private Long id;

    /**
     * 既有行版本号；新增行为 {@code null}。
     */
    @Min(value = 0, message = "版本号不能小于0")
    private Integer version;

    @NotNull(message = "商品规格ID不能为空")
    @Positive(message = "商品规格ID必须大于0")
    private Long skuId;

    @NotBlank(message = "采购单位不能为空")
    @Size(max = 32, message = "采购单位不能超过32个字符")
    private String purchaseUnit;

    /**
     * 参考价，4 位定点字符串；可空。
     */
    @Pattern(regexp = ScmDecimalStrings.PATTERN, message = "参考价格式不正确")
    @JsonDeserialize(using = ScmStrictDecimalStringDeserializer.class)
    private String referencePrice;

    /**
     * 默认采购员，引用 SmartAdmin {@code t_employee.employee_id}。
     */
    @Positive(message = "默认采购员 ID 必须大于0")
    private Long purchaserId;

    @NotNull(message = "是否为默认采购商品不能为空")
    private Boolean defaultFlag = false;

    /**
     * 缺省为 {@code ENABLED}。
     */
    @ScmEnumValue(enumClass = ScmEnableStatusEnum.class, message = "供应商商品状态无效")
    private String status;
}
