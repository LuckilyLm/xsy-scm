package net.lab1024.sa.admin.module.scm.product.domain.form;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.*;
import java.math.BigDecimal;

@Data
public class ProductSkuForm {
    private Long skuId;
    private Integer version;
    @NotBlank
    @Size(max = 64)
    private String skuCode;
    @Size(max = 64)
    private String barcode;
    @NotBlank
    @Size(max = 150)
    private String specName;
    @NotNull
    @Size(max = 30)
    private Map<@NotBlank @Size(max = 100) String, @NotBlank @Size(max = 150) String> specValues = new LinkedHashMap<>();
    @NotBlank
    @Size(max = 32)
    private String saleUnit;
    @NotNull
    @Pattern(regexp = "STANDARD|NON_STANDARD")
    private String productType;
    @NotNull
    @DecimalMin("0.0000")
    @Digits(integer = 14, fraction = 4)
    private BigDecimal marketPrice;
    @NotNull
    @Pattern(regexp = "ON_SHELF|OFF_SHELF")
    private String status;
    @NotNull
    private Boolean defaultFlag;
    @NotNull
    @Min(0)
    private Integer sortOrder = 0;
}
