package com.xianshuyuan.scm.marketing.dto;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;

/**
 * 新建 / 修改首页板块请求。
 */
public record HomeSectionSaveRequest(
        @NotBlank(message = "板块类型不能为空") @Size(max = 16) String sectionType,
        @Size(max = 100, message = "板块标题过长") String title,
        Long promotionId,
        Long categoryId,
        Integer sortOrder,
        @Size(max = 16) String status,
        OffsetDateTime startAt,
        OffsetDateTime endAt,
        JsonNode payload
) {
}
