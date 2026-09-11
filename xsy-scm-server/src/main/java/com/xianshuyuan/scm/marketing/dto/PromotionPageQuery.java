package com.xianshuyuan.scm.marketing.dto;

public record PromotionPageQuery(long page, long pageSize, String keyword, String type, String status) {
}
