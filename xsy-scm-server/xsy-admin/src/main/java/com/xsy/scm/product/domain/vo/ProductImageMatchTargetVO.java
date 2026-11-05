package com.xsy.scm.product.domain.vo;

import lombok.Data;

/**
 * 图片批量匹配只需要「编码 → 商品 ID」，不带上架状态、规格和图片，避免把列表查询放大成大对象。
 *
 * <p>
 * 带上名称只为让手动改绑时的下拉框能直接显示「名称（编码）」。
 */
@Data
public class ProductImageMatchTargetVO {
    private Long spuId;
    private String spuCode;
    private String name;
}
