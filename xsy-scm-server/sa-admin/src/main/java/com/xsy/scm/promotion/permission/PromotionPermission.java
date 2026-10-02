package com.xsy.scm.promotion.permission;

/**
 * 营销中心发布的权限码。
 *
 * <p>
 * 活动与券的维护分开授权，启停再单独一个：能改活动内容的人不必然能把它「上线」，
 * 而活动一旦生效就会影响所有订单的金额。
 */
public final class PromotionPermission {

    public static final String ACTIVITY_QUERY = "scm:promotion:activity:query";

    public static final String ACTIVITY_EDIT = "scm:promotion:activity:edit";

    public static final String ACTIVITY_STATUS = "scm:promotion:activity:status";

    public static final String COUPON_QUERY = "scm:promotion:coupon:query";

    public static final String COUPON_EDIT = "scm:promotion:coupon:edit";

    public static final String COUPON_ISSUE = "scm:promotion:coupon:issue";

    private PromotionPermission() {
    }
}
