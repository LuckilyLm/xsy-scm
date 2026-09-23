package net.lab1024.sa.admin.module.scm.product.domain.vo;

import lombok.Data;

import java.util.List;

/** 图片中心单 SPU 视图：商品摘要 + 其全部图片（fileUrl 由 fileKey 现算，不入库）。 */
@Data
public class ProductImageCenterVO {
    private Long spuId;
    private String spuCode;
    private String name;
    private List<ProductImageVO> images;
}
