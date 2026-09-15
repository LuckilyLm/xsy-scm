package net.lab1024.sa.admin.module.scm.product.domain.form;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.util.*;
import java.math.BigDecimal;
@Data
public class ProductCategoryDeleteForm {
@NotNull @Positive private Long categoryId;
@NotNull @Min(0) private Integer version;
}
