package com.xsy.scm.product.domain.vo;

import lombok.Data;

import java.util.List;

/**
 * 图片中心单 SPU 视图：商品摘要 + 其全部图片（fileUrl 由 fileKey 实时生成，不单独存储）。
 */
@Data
public class ProductImageCenterVO {
    private Long spuId;
    private String spuCode;
    private String name;
    private List<ProductImageVO> images;
}
