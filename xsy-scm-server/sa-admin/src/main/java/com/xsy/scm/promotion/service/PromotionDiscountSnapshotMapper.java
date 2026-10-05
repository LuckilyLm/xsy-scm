package com.xsy.scm.promotion.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xsy.scm.promotion.domain.entity.PromotionActivityEntity;
import com.xsy.scm.promotion.domain.vo.PromotionDiscountVO;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 将订单优惠计算结果转换为确认订单保存的不可变快照。 */
final class PromotionDiscountSnapshotMapper {

    private PromotionDiscountSnapshotMapper() {
    }

    static Map<String, Object> couponSnapshot(PromotionDiscountVO vo) {
        if (vo.getCouponInstanceId() == null) {
            return null;
        }
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("couponInstanceId", vo.getCouponInstanceId());
        snapshot.put("couponCode", vo.getCouponCode());
        snapshot.put("couponName", vo.getCouponName());
        snapshot.put("couponDiscount", plain(vo.getCouponDiscount()));
        return snapshot;
    }

    /**
     * 冻结每条实际生效活动及被互斥组挤掉的活动，不能只保存主活动。
     *
     * <p>
     * 退款按冻结分摊反向追加事实；漏掉叠加活动就无法还原原优惠。顶层保持为 object 以满足 JSONB 约束。
     */
    static Map<String, Object> activitySnapshot(PromotionDiscountVO vo) {
        List<Map<String, Object>> applied = new ArrayList<>();
        for (PromotionDiscountVO.AppliedActivityVO activity : vo.getAppliedActivities()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("activityId", activity.getActivityId());
            row.put("activityCode", activity.getActivityCode());
            row.put("activityName", activity.getActivityName());
            row.put("activityType", activity.getActivityType());
            row.put("version", activity.getVersion());
            row.put("rule", activity.getRule());
            row.put("discountAmount", plain(activity.getDiscountAmount()));
            applied.add(row);
        }
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("applied", applied);
        snapshot.put("suppressed", List.copyOf(vo.getSuppressedActivities()));
        return snapshot;
    }

    static PromotionDiscountVO.AppliedActivityVO appliedActivity(PromotionActivityEntity activity,
            BigDecimal discountAmount) {
        PromotionDiscountVO.AppliedActivityVO vo = new PromotionDiscountVO.AppliedActivityVO();
        vo.setActivityId(activity.getId());
        vo.setActivityCode(activity.getActivityCode());
        vo.setActivityName(activity.getActivityName());
        vo.setActivityType(activity.getActivityType());
        vo.setVersion(activity.getVersion());
        vo.setRule(activity.getRule());
        vo.setDiscountAmount(discountAmount);
        return vo;
    }

    static String allocationsJson(ObjectMapper objectMapper, PromotionDiscountVO vo) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (PromotionDiscountVO.PromotionDiscountAllocationVO allocation : vo.getAllocations()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("orderItemId", allocation.getOrderItemId());
            row.put("baseAmount", plain(allocation.getBaseAmount()));
            row.put("discountAmount", plain(allocation.getDiscountAmount()));
            rows.add(row);
        }
        try {
            return objectMapper.writeValueAsString(rows);
        } catch (com.fasterxml.jackson.core.JsonProcessingException exception) {
            throw new IllegalStateException("订单优惠分摊序列化失败", exception);
        }
    }

    private static String plain(BigDecimal value) {
        return value == null ? null : value.toPlainString();
    }
}
