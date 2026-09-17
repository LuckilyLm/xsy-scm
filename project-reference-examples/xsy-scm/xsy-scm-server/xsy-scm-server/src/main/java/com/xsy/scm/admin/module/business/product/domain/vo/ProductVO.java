package com.xsy.scm.admin.module.business.product.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import com.xsy.scm.admin.module.business.product.constant.MeasureTypeEnum;
import com.xsy.scm.admin.module.business.product.constant.ProductStatusEnum;
import com.xsy.scm.admin.module.business.product.constant.ProductTypeEnum;
import com.xsy.scm.admin.module.business.product.constant.PurchaseModeEnum;
import com.xsy.scm.base.common.swagger.SchemaEnum;

import java.time.LocalDateTime;

/**
 * 商品 返回对象
 *
 * @author xsy-scm
 */
@Data
public class ProductVO {

    @Schema(description = "商品ID")
    private Long productId;

    @Schema(description = "商品编码")
    private String productNo;

    @Schema(description = "三级分类ID")
    private Long categoryId;

    @Schema(description = "分类名称")
    private String categoryName;

    @Schema(description = "商品名称")
    private String productName;

    @SchemaEnum(ProductTypeEnum.class)
    private Integer productType;

    @SchemaEnum(MeasureTypeEnum.class)
    private Integer measureType;

    @Schema(description = "基本单位")
    private String baseUnit;

    @Schema(description = "是否多规格")
    private Boolean specFlag;

    @SchemaEnum(PurchaseModeEnum.class)
    private Integer purchaseMode;

    @Schema(description = "默认供应商ID")
    private Long defaultSupplierId;

    @Schema(description = "默认采购员ID")
    private Long defaultBuyerId;

    @Schema(description = "商品详情")
    private String detail;

    @Schema(description = "主图")
    private String mainImage;

    @SchemaEnum(ProductStatusEnum.class)
    private Integer status;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    @Schema(description = "更新时间")
    private LocalDateTime updateTime;
}
