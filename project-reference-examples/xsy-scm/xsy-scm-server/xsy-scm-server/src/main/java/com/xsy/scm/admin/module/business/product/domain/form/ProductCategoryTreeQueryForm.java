package com.xsy.scm.admin.module.business.product.domain.form;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;

/**
 * 产品分类 树查询 form
 *
 * @author xsy-scm
 */
@Data
public class ProductCategoryTreeQueryForm implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "父级id，为空时查询整棵树")
    private Long parentId;
}
