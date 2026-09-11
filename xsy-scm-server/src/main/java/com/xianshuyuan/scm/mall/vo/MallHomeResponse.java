package com.xianshuyuan.scm.mall.vo;

import com.xianshuyuan.scm.marketing.vo.PromotionResponse;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 商城首页统一聚合响应，供 Web 与 Taro 共用。
 */
public record MallHomeResponse(MallThemeConfig theme, List<MallHomeSectionResponse> sections,
                               List<MallCategoryResponse> categories, List<PromotionResponse> promotions,
                               OffsetDateTime generatedAt) {
}
