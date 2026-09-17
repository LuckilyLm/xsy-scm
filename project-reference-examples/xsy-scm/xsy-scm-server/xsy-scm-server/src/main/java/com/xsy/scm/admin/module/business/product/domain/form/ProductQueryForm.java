package com.xsy.scm.admin.module.business.product.domain.form;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.Data;
import com.xsy.scm.admin.module.business.product.constant.ProductStatusEnum;
import com.xsy.scm.admin.module.business.product.constant.ProductTypeEnum;
import com.xsy.scm.base.common.domain.PageParam;
import com.xsy.scm.base.common.swagger.SchemaEnum;
import com.xsy.scm.base.common.validator.enumeration.CheckEnum;

/**
 * 商品 分页查询表单
 *
 * @author xsy-scm
 */
@Data
public class ProductQueryForm extends PageParam {

    @Schema(description = "三级分类ID")
    private Long categoryId;

    @Schema(description = "搜索词（商品名称 / 编码）")
    @Size(max = 30, message = "搜索词最多30字符")
    private String searchWord;

    @SchemaEnum(ProductTypeEnum.class)
    @CheckEnum(message = "商品类型错误", value = ProductTypeEnum.class, required = false)
    private Integer productType;

    @SchemaEnum(ProductStatusEnum.class)
    @CheckEnum(message = "商品状态错误", value = ProductStatusEnum.class, required = false)
    private Integer status;

    @Schema(hidden = true)
    private Boolean deletedFlag;
}
