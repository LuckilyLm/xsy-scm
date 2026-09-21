package net.lab1024.sa.admin.module.scm.product.domain.form;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.util.*;
import java.math.BigDecimal;
@Data
public class ProductImageForm {
private Long imageId;
private Integer version;
@NotBlank @Size(max=255) private String fileKey;
@Size(max=255) private String fileName;
@Min(0) private Long fileSize;
@NotNull private Boolean primaryFlag = false;
@NotNull @Min(0) private Integer sortOrder = 0;
}
