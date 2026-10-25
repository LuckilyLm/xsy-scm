package com.xsy.scm.mall.decoration.domain.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.xsy.scm.common.json.ScmOperatorNameSerializer;

import java.time.OffsetDateTime;

import lombok.Data;

@Data
public class MallPageVO {
    private String pageCode;
    private String title;
    /** 后台草稿正文。 */
    private String content;
    private Integer version;
    /** 已发布正文；未发布过时为 null。 */
    private String publishedContent;
    private Integer publishedVersion;
    private OffsetDateTime publishedAt;
    @JsonSerialize(using = ScmOperatorNameSerializer.class)
    private String publishedBy;
    private OffsetDateTime updatedAt;
    @JsonSerialize(using = ScmOperatorNameSerializer.class)
    private String updatedBy;
    /** 草稿版本与发布版本不一致时提示「有未发布修改」。 */
    private Boolean unpublishedChanges;
}
