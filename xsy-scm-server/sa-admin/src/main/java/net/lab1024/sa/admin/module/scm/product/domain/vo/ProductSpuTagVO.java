package net.lab1024.sa.admin.module.scm.product.domain.vo;

import lombok.Data;

/**
 * 商品行上的标签摘要；停用的历史标签仍返回，避免已打标商品丢字段。
 */
@Data
public class ProductSpuTagVO {
    private Long spuId;
    private Long tagId;
    private String tagCode;
    private String name;
    private String status;
}
