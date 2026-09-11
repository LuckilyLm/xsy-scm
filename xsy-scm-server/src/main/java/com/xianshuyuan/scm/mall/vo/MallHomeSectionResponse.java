package com.xianshuyuan.scm.mall.vo;

import com.fasterxml.jackson.databind.JsonNode;
import com.xianshuyuan.scm.marketing.entity.HomeSectionType;

/**
 * 面向商城客户端的首页板块配置，不暴露后台审计字段。
 */
public record MallHomeSectionResponse(Long id, HomeSectionType sectionType, String title,
                                      Integer sortOrder, JsonNode payload, Long promotionId,
                                      Long categoryId) {
}
