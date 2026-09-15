package net.lab1024.sa.admin.module.scm.product.domain.form;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.util.*;
import java.math.BigDecimal;
@Data
public class ProductCategoryAddForm {
@Positive private Long parentId;
@NotBlank @Size(max=64) private String categoryCode;
@NotBlank @Size(max=100) private String name;
@NotNull @Min(0) private Integer sortOrder = 0;
@NotNull @Pattern(regexp="ENABLED|DISABLED") private String status;
}
