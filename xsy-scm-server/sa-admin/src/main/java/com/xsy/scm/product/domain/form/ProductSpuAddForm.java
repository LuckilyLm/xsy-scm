package com.xsy.scm.product.domain.form;

import com.xsy.scm.common.constant.ScmShelfStatusEnum;
import com.xsy.scm.common.validation.ScmEnumValue;
import com.xsy.scm.product.constant.ScmProductMasterStatusEnum;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;
import java.math.BigDecimal;

@Data
public class ProductSpuAddForm {
    @NotBlank(message = "SPU 编码不能为空")
    @Size(max = 64, message = "SPU 编码不能超过64个字符")
    private String spuCode;
    @NotBlank(message = "商品名称不能为空")
    @Size(max = 150, message = "商品名称不能超过150个字符")
    private String name;
    @Size(max = 150, message = "商品别名不能超过150个字符")
    private String alias;
    @NotNull(message = "分类 ID不能为空")
    @Positive(message = "分类 ID必须大于0")
    private Long categoryId;
    @Size(max = 1000, message = "商品描述不能超过1000个字符")
    private String description;
    @NotNull(message = "商品销售状态不能为空")
    @ScmEnumValue(enumClass = ScmShelfStatusEnum.class, message = "商品销售状态无效")
    private String status;
    @NotEmpty(message = "SKU 列表不能为空")
    @Size(max = 200, message = "SKU 列表不能超过200项")
    @Valid
    private List<ProductSkuForm> skuList;
    @NotNull(message = "商品图片列表不能为空")
    @Size(max = 20, message = "商品图片列表不能超过20项")
    @Valid
    private List<ProductImageForm> images = new ArrayList<>();
    @Size(max = 64, message = "助记码不能超过64个字符")
    private String mnemonicCode;
    @Size(max = 100, message = "品牌名称不能超过100个字符")
    private String brandName;
    @Size(max = 100, message = "产地不能超过100个字符")
    private String origin;
    @Pattern(regexp = "AMBIENT|CHILLED|FROZEN", message = "储存方式取值无效")
    private String storageMethod;
    @Min(value = 0, message = "保质期天数不能小于0")
    @Max(value = 36500, message = "保质期天数不能大于36500")
    private Integer shelfLifeDays;
    @DecimalMin(value = "0", message = "损耗率不能小于0")
    @DecimalMax(value = "100", message = "损耗率不能大于100")
    private BigDecimal lossRate;
    @Min(value = 0, message = "采购预警天数不能小于0")
    @Max(value = 365, message = "采购预警天数不能大于365")
    private Integer purchaseWarningDays;
    @Size(max = 100, message = "开票名称不能超过100个字符")
    private String invoiceName;
    @Size(max = 32, message = "税收分类编码不能超过32个字符")
    private String taxCategoryCode;
    @NotNull(message = "免税标记不能为空")
    private Boolean taxExempt = false;
    @DecimalMin(value = "0", message = "税率不能小于0")
    @DecimalMax(value = "100", message = "税率不能大于100")
    private BigDecimal taxRate;
    /**
     * 主档生命周期；null 表示新增时取 ENABLED、编辑时保持原值，上下架仍由 status 表达。
     */
    @ScmEnumValue(enumClass = ScmProductMasterStatusEnum.class, message = "商品主档状态无效")
    private String masterStatus;
    @Size(max = 50, message = "标签 ID 列表不能超过50项")
    @NotNull(message = "标签 ID 列表不能为空")
    private List<
            @NotNull(message = "标签 ID 列表不能为空")
            @Positive(message = "标签 ID 列表必须大于0")
            Long> tagIds = new ArrayList<>();
}
