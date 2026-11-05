package com.xsy.scm.product.domain.vo;

import lombok.Data;

@Data
public class ProductImageVO {
    private Long imageId;
    private Integer version;
    private String fileKey;
    private String fileUrl;
    private String fileName;
    private Long fileSize;
    private Boolean primaryFlag;
    private Integer sortOrder;
}
