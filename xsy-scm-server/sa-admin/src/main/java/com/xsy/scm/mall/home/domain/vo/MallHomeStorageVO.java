package com.xsy.scm.mall.home.domain.vo;

import java.time.OffsetDateTime;
import lombok.Data;

@Data
public class MallHomeStorageVO {
    private Integer version;
    private Long currentReleaseId;
    private String content;
    private OffsetDateTime updatedAt;
    private String updatedBy;
}
