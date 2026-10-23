package com.xsy.scm.mall.home.domain.vo;

import java.time.OffsetDateTime;

import lombok.Data;

@Data
public class MallHomeStorageVO {
    private Integer version;
    private Long currentReleaseId;
    private String content;
    /**
     * 主题编码；随发布版本一起回滚。
     */
    private String themeCode;
    private OffsetDateTime updatedAt;
    private String updatedBy;
}
