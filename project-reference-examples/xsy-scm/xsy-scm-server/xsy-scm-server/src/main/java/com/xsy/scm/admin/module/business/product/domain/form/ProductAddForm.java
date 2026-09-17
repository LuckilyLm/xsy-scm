package com.xsy.scm.admin.module.business.product.domain.form;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import com.xsy.scm.admin.module.business.product.constant.MeasureTypeEnum;
import com.xsy.scm.admin.module.business.product.constant.ProductStatusEnum;
import com.xsy.scm.admin.module.business.product.constant.ProductTypeEnum;
import com.xsy.scm.admin.module.business.product.constant.PurchaseModeEnum;
import com.xsy.scm.base.common.swagger.SchemaEnum;
import com.xsy.scm.base.common.validator.enumeration.CheckEnum;

/**
 * 商品 添加表单
 *
 * @author xsy-scm
 */
@Data
public class ProductAddForm {

    @Schema(description = "三级分类ID")
    @NotNull(message = "三级分类不能为空")
    private Long categoryId;

    @Schema(description = "商品名称")
    @NotBlank(message = "商品名称不能为空")
    private String productName;

    @SchemaEnum(ProductTypeEnum.class)
    @CheckEnum(message = "商品类型错误", value = ProductTypeEnum.class, required = true)
    private Integer productType;

    @SchemaEnum(MeasureTypeEnum.class)
    @CheckEnum(message = "计量方式错误", value = MeasureTypeEnum.class, required = true)
    private Integer measureType;

    @Schema(description = "基本单位，如 件 / kg")
    @NotBlank(message = "基本单位不能为空")
    private String baseUnit;

    @Schema(description = "是否多规格")
    private Boolean specFlag;

    @SchemaEnum(PurchaseModeEnum.class)
    @CheckEnum(message = "采购方式错误", value = PurchaseModeEnum.class, required = false)
    private Integer purchaseMode;

    @Schema(description = "默认供应商ID")
    private Long defaultSupplierId;

    @Schema(description = "默认采购员ID")
    private Long defaultBuyerId;

    @Schema(description = "商品详情")
    private String detail;

    @Schema(description = "主图（文件服务地址）")
    private String mainImage;

    @SchemaEnum(ProductStatusEnum.class)
    @CheckEnum(message = "商品状态错误", value = ProductStatusEnum.class, required = false)
    private Integer status;
}
