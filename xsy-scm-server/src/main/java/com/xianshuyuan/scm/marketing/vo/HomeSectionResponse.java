package com.xianshuyuan.scm.marketing.vo;

import com.fasterxml.jackson.databind.JsonNode;
import com.xianshuyuan.scm.marketing.entity.HomeSectionType;

import java.time.OffsetDateTime;

/**
 * 首页板块视图。前端按 sectionType 选择渲染方式，payload 承载板块自定义配置。
 */
public record HomeSectionResponse(Long id, HomeSectionType sectionType, String title, Long promotionId,
                                  Long categoryId, Integer sortOrder, String status, OffsetDateTime startAt,
                                  OffsetDateTime endAt, JsonNode payload) {
}
