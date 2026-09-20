package net.lab1024.sa.admin.module.scm.product.domain.form;
import jakarta.validation.constraints.*;
import lombok.Data;
@Data
public class ProductTagAddForm {
@NotBlank @Size(max=64) private String tagCode;
@NotBlank @Size(max=64) private String name;
@NotNull @Pattern(regexp="ENABLED|DISABLED") private String status;
@NotNull @Min(0) private Integer sortOrder = 0;
}
