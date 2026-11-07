package com.xsy.scm.mall.home.domain.vo;

import com.xsy.scm.mall.home.domain.form.MallHomeBlockForm;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.xsy.scm.common.json.ScmOperatorNameSerializer;

import java.time.OffsetDateTime;
import java.util.List;

/** 首页草稿及其可发布的顶部渐变配色。 */
public record MallHomeDraftVO(Integer version, Integer pointerVersion, Long currentReleaseId, String themeCode,
    String themeColor, String headerStartColor, String headerEndColor, List<MallHomeBlockForm> blocks,
    OffsetDateTime updatedAt, @JsonSerialize(using = ScmOperatorNameSerializer.class) String updatedBy) {
}
