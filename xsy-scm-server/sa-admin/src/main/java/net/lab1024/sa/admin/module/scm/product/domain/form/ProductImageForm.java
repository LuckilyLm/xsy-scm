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
/**
 * 图片内容角色：GALLERY 图集 / DETAIL 详情图，与是否主图无关。
 * 新增行留空即按图集归类；已有行的取值一律以库内现值为准，本字段不参与改写。
 */
@Pattern(regexp = "GALLERY|DETAIL", message = "图片类型只能是 GALLERY 或 DETAIL")
private String imageType;
}
