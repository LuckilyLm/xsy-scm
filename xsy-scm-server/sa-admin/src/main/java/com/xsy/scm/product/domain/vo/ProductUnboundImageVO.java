package com.xsy.scm.product.domain.vo;

import lombok.Data;

import java.time.LocalDateTime;

/** 图片中心里「已上传但还没挂到任何商品」的图片：数据源是文件表，不是商品图片表。 */
@Data
public class ProductUnboundImageVO {
    private String fileKey;
    private String fileUrl;
    private String fileName;
    private Long fileSize;
    private LocalDateTime createTime;
}
