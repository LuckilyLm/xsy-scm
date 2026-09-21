package net.lab1024.sa.admin.module.scm.product.domain.vo;

import lombok.Data;

@Data
public class ProductTagVO {
    private Long tagId;
    private Integer version;
    private String tagCode;
    private String name;
    private String status;
    private Integer sortOrder;
    /**
     * 当前挂了这个标签的 SPU 数；大于 0 时禁止删除。
     */
    private Long productCount;
}
