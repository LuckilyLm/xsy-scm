package net.lab1024.sa.admin.module.scm.product.domain.form;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.util.*;
import java.math.BigDecimal;
@Data
public class ProductSpuAddForm {
@NotBlank @Size(max=64) private String spuCode;
@NotBlank @Size(max=150) private String name;
@Size(max=150) private String alias;
@NotNull @Positive private Long categoryId;
@Size(max=1000) private String description;
@NotNull @Pattern(regexp="ON_SHELF|OFF_SHELF") private String status;
@NotEmpty @Size(max=200) @Valid private List<ProductSkuForm> skuList;
@NotNull @Size(max=20) @Valid private List<ProductImageForm> images = new ArrayList<>();
}
