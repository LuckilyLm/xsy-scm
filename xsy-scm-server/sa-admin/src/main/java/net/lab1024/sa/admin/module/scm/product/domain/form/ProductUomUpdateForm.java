package net.lab1024.sa.admin.module.scm.product.domain.form;

import jakarta.validation.constraints.*;
import lombok.Data;

/**
 * 计量单位编辑表单。编码与名称刻意不可改：商品与供应商关系表按单位名称字符串记账，
 * 改名会让既有数据指向一个字典里不存在的单位，只能停用旧单位再新建。
 */
@Data
public class ProductUomUpdateForm {
    @NotNull
    @Positive
    private Long uomId;
    @NotNull
    @Min(0)
    private Integer version;
    @NotBlank
    @Pattern(regexp = "WEIGHT|COUNT|VOLUME|LENGTH|OTHER")
    private String category;
    @NotNull
    @Min(0)
    @Max(6)
    private Integer precisionScale;
    @NotNull
    @Pattern(regexp = "ENABLED|DISABLED")
    private String status;
    @NotNull
    @Min(0)
    private Integer sortOrder;
}
