package com.xsy.scm.mall.home.domain.vo;

import java.time.OffsetDateTime;

import lombok.Data;
import com.xsy.scm.common.json.ScmOperatorNameSerializer;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

@Data
public class MallHomePopupStorageVO {
    private Integer version;
    private Boolean enabled;
    private String displayRule;
    private OffsetDateTime startAt;
    private OffsetDateTime endAt;
    private String content;
    private OffsetDateTime updatedAt;
    @JsonSerialize(using = ScmOperatorNameSerializer.class)
    private String updatedBy;
}
