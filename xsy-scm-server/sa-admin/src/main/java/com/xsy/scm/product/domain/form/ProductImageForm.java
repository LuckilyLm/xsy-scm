package com.xsy.scm.product.domain.form;

import com.xsy.scm.common.validation.ScmEnumValue;
import com.xsy.scm.product.constant.ScmProductImageTypeEnum;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ProductImageForm {
    private Long imageId;
    private Integer version;
    @NotBlank(message = "图片文件标识不能为空")
    @Size(max = 255, message = "图片文件标识不能超过255个字符")
    private String fileKey;
    @Size(max = 255, message = "图片文件名不能超过255个字符")
    private String fileName;
    @Min(value = 0, message = "图片文件大小不能小于0")
    private Long fileSize;
    @NotNull(message = "主图标记不能为空")
    private Boolean primaryFlag = false;
    @NotNull(message = "排序值不能为空")
    @Min(value = 0, message = "排序值不能小于0")
    private Integer sortOrder = 0;
    /**
     * 图片内容角色：GALLERY 图集 / DETAIL 详情图，与是否主图无关。新增行留空即按图集归类；已有行的取值一律以库内现值为准，本字段不参与改写。
     */
    @ScmEnumValue(enumClass = ScmProductImageTypeEnum.class, message = "图片类型只能是 GALLERY 或 DETAIL")
    private String imageType;
}
