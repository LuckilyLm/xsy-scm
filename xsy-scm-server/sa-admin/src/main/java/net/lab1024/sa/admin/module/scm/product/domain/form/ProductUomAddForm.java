package net.lab1024.sa.admin.module.scm.product.domain.form;
import jakarta.validation.constraints.*;
import lombok.Data;
@Data
public class ProductUomAddForm {
@NotBlank @Size(max=64) private String uomCode;
@NotBlank @Size(max=32) private String name;
@NotBlank @Pattern(regexp="WEIGHT|COUNT|VOLUME|LENGTH|OTHER") private String category;
@NotNull @Min(0) @Max(6) private Integer precisionScale = 4;
@NotNull @Pattern(regexp="ENABLED|DISABLED") private String status;
@NotNull @Min(0) private Integer sortOrder = 0;
}
