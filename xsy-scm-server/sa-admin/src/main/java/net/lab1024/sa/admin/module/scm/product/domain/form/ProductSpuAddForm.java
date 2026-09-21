package net.lab1024.sa.admin.module.scm.product.domain.form;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.util.*;
import java.math.BigDecimal;

@Data
public class ProductSpuAddForm {
    @NotBlank
    @Size(max = 64)
    private String spuCode;
    @NotBlank
    @Size(max = 150)
    private String name;
    @Size(max = 150)
    private String alias;
    @NotNull
    @Positive
    private Long categoryId;
    @Size(max = 1000)
    private String description;
    @NotNull
    @Pattern(regexp = "ON_SHELF|OFF_SHELF")
    private String status;
    @NotEmpty
    @Size(max = 200)
    @Valid
    private List<ProductSkuForm> skuList;
    @NotNull
    @Size(max = 20)
    @Valid
    private List<ProductImageForm> images = new ArrayList<>();
    @Size(max = 64)
    private String mnemonicCode;
    @Size(max = 100)
    private String brandName;
    @Size(max = 100)
    private String origin;
    @Pattern(regexp = "AMBIENT|CHILLED|FROZEN")
    private String storageMethod;
    @Min(0)
    @Max(36500)
    private Integer shelfLifeDays;
    @DecimalMin("0")
    @DecimalMax("100")
    private BigDecimal lossRate;
    @Min(0)
    @Max(365)
    private Integer purchaseWarningDays;
    @Size(max = 100)
    private String invoiceName;
    @Size(max = 32)
    private String taxCategoryCode;
    @NotNull
    private Boolean taxExempt = false;
    @DecimalMin("0")
    @DecimalMax("100")
    private BigDecimal taxRate;
    /**
     * 主档生命周期；null 表示新增时取 ENABLED、编辑时保持原值，上下架仍由 status 表达。
     */
    @Pattern(regexp = "ENABLED|DISABLED|ARCHIVED")
    private String masterStatus;
    @Size(max = 50)
    @NotNull
    private List<@NotNull @Positive Long> tagIds = new ArrayList<>();
}
