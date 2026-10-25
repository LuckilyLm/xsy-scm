package com.xsy.scm.mall.decoration.domain.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.xsy.scm.common.json.ScmOperatorNameSerializer;

import java.time.OffsetDateTime;

import lombok.Data;

@Data
public class MallStoreVO {
    private Integer version;
    private String storeName;
    private String storeLink;
    private OffsetDateTime updatedAt;
    @JsonSerialize(using = ScmOperatorNameSerializer.class)
    private String updatedBy;
}
