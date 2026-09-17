package com.xsy.scm.admin.module.business.product.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 产品分类 层级树 vo
 *
 * @author xsy-scm
 */
@Data
public class ProductCategoryTreeVO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "分类id")
    private Long categoryId;

    @Schema(description = "分类名称")
    private String categoryName;

    @Schema(description = "父级id")
    private Long parentId;

    @Schema(description = "分类id")
    private Long value;

    @Schema(description = "分类名称")
    private String label;

    @Schema(description = "子类")
    private List<ProductCategoryTreeVO> children;
}
