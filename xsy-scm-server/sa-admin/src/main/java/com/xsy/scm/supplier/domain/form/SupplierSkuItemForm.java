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
 * 商品关联带 id 表示更新，且必须携带匹配的版本；不带 id 表示新增。未提交的旧关联会删除，商品规格不能通过更新变更。
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

    /**
     * 外部供应商货号可空，保留原始大小写和前导零，不自动拼接。
     */
    @Size(max = 100, message = "供应商货号不能超过100个字符")
    private String supplierItemCode;

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
