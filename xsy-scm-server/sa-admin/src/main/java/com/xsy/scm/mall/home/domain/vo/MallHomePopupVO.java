package com.xsy.scm.mall.home.domain.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.xsy.scm.common.json.ScmOperatorNameSerializer;

import java.time.OffsetDateTime;
import java.util.List;

import lombok.Data;

@Data
public class MallHomePopupVO {
    private Integer version;
    private Boolean enabled;
    private String displayRule;
    private OffsetDateTime startAt;
    private OffsetDateTime endAt;
    private List<MallHomePopupItemVO> items;
    /** 当前时间是否落在生效窗口内；管理端据此提示，客户端只在 active 时展示。 */
    private Boolean active;
    private OffsetDateTime updatedAt;
    @JsonSerialize(using = ScmOperatorNameSerializer.class)
    private String updatedBy;
}
