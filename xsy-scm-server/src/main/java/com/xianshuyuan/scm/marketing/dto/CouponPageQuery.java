package com.xianshuyuan.scm.marketing.dto;

public record CouponPageQuery(long page, long pageSize, String keyword, String status) {
}
