package com.xsy.scm.mall.home.domain.vo;

import java.time.OffsetDateTime;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.xsy.scm.common.json.ScmOperatorNameSerializer;
import lombok.Data;

@Data
public class MallHomeReleaseVO {
    private Long id;
    private String content;
    private String themeCode;
    private String publishKind;
    private Long sourceReleaseId;
    private String reason;
    private OffsetDateTime publishedAt;
    @JsonSerialize(using = ScmOperatorNameSerializer.class)
    private String publishedBy;
}
