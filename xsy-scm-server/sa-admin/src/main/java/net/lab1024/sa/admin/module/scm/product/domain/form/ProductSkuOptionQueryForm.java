package net.lab1024.sa.admin.module.scm.product.domain.form;

import lombok.Data;
import jakarta.validation.constraints.*;

@Data
public class ProductSkuOptionQueryForm {
    @Size(max = 150)
    private String keyword;
    @Pattern(regexp = "ON_SHELF|OFF_SHELF")
    private String status;
    private Long spuId;
    @NotNull
    @Min(1)
    @Max(200)
    private Integer limit = 50;
}
