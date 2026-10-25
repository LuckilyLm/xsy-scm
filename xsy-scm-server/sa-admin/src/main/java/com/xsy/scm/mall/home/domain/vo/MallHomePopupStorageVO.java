package com.xsy.scm.mall.home.domain.vo;

import java.time.OffsetDateTime;

import lombok.Data;

@Data
public class MallHomePopupStorageVO {
    private Integer version;
    private Boolean enabled;
    private String displayRule;
    private OffsetDateTime startAt;
    private OffsetDateTime endAt;
    private String content;
    private OffsetDateTime updatedAt;
    private String updatedBy;
}
