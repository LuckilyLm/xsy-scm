package com.xsy.scm.mall.home.domain.vo;

import java.time.OffsetDateTime;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.xsy.scm.common.json.ScmOperatorNameSerializer;
import lombok.Data;

/** 首页发布快照，回滚时同时恢复组件与顶部配色。 */
@Data
public class MallHomeReleaseVO {
    private Long id;
    private String content;
    private String themeCode;
    private String themeColor;
    private String headerStartColor;
    private String headerEndColor;
    private String publishKind;
    private Long sourceReleaseId;
    private String reason;
    private OffsetDateTime publishedAt;
    @JsonSerialize(using = ScmOperatorNameSerializer.class)
    private String publishedBy;
}
