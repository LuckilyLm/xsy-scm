package com.xsy.scm.promotion.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xsy.scm.common.constant.ScmOperator;
import com.xsy.scm.common.exception.ScmBusinessException;
import com.xsy.scm.promotion.constant.PromotionErrorCode;
import com.xsy.scm.promotion.constant.ScmPromotionActivityTypeEnum;
import com.xsy.scm.promotion.constant.ScmPromotionCouponDiscountTypeEnum;
import com.xsy.scm.promotion.constant.ScmPromotionCouponInstanceStatusEnum;
import com.xsy.scm.promotion.constant.ScmPromotionStatusEnum;
import com.xsy.scm.promotion.dao.OrderDiscountDao;
import com.xsy.scm.promotion.dao.PromotionActivityDao;
import com.xsy.scm.promotion.dao.PromotionCouponDao;
import com.xsy.scm.promotion.dao.PromotionCouponInstanceDao;
import com.xsy.scm.promotion.domain.entity.OrderDiscountEntity;
import com.xsy.scm.promotion.domain.entity.PromotionActivityEntity;
import com.xsy.scm.promotion.domain.entity.PromotionCouponEntity;
import com.xsy.scm.promotion.domain.entity.PromotionCouponInstanceEntity;
import com.xsy.scm.promotion.domain.form.PromotionDiscountPreviewForm;
import com.xsy.scm.promotion.domain.vo.OrderDiscountVO;
import com.xsy.scm.promotion.domain.vo.PromotionDiscountVO;
import com.xsy.scm.promotion.support.PromotionDiscountAllocator;
import com.xsy.scm.promotion.support.PromotionOrderFacts;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 优惠计算：试算（只读）与冻结（占用券 + 落库）。
 *
 * <p>
 * 分工与边界：
 * <ul>
 * <li><b>基础价不在本域</b>：订单行只传「行 id + 基础金额」，基础定价顺序
 * （协议价 → 客户类型价 → 市场价）仍只有订单域一处实现，活动价在它之后计算。</li>
 * <li><b>试算不占用</b>：{@code preview} 不写任何表、不动券状态（ADR-009：预览不等于最终占用）。</li>
 * <li><b>冻结只认订单事实</b>：{@code freeze} 由订单确认在服务端编排调用，客户与行金额来自
 * {@link PromotionOrderFacts}，活动由服务端自行选出；客户端无法指定客户、行金额或优惠组合。</li>
 * <li><b>冻结时重验</b>：{@code freeze} 重新读活动与券的**当前**状态与版本，
 * 试算之后活动可能已停用、券可能已被别人占用 —— 用试算结果直接落库会写出一个从未成立过的优惠。</li>
 * <li><b>互斥组</b>：同组内只取优先级最高的一条，其余记入 {@code suppressedActivities} 供解释；
 * 不同组可以叠加，顺序按优先级降序、id 升序，保证同一份输入得到同一份结果。</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class PromotionDiscountService {

    private static final int SCALE = 4;

    private final PromotionActivityDao promotionActivityDao;

    private final PromotionCouponDao promotionCouponDao;

    private final PromotionCouponInstanceDao promotionCouponInstanceDao;

    private final OrderDiscountDao orderDiscountDao;

    private final ObjectMapper objectMapper;

    /**
     * 试算：只读，不占用券。
     *
     * <p>
     * 试算面向「还没有订单」的结算预览，因此客户与行由调用方给出；它不写任何表，
     * 因此不构成「客户端自行组合优惠」——真正落库的冻结只认订单事实。
     */
    @Transactional(readOnly = true)
    public PromotionDiscountVO preview(PromotionDiscountPreviewForm form) {
        List<PromotionDiscountAllocator.Line> lines = form.getLines().stream()
                .map(line -> new PromotionDiscountAllocator.Line(line.getOrderItemId(), amount(line.getBaseAmount())))
                .toList();
        return compute(form.getCustomerId(), form.getActivityId(), form.getCouponInstanceId(), lines, null);
    }

    /**
     * 冻结：由**订单确认**在服务端编排调用，按订单事实计算并占用券。
     *
     * <p>
     * 与试算的三点差别：
     * <ul>
     * <li>客户与行金额来自 {@link PromotionOrderFacts}（订单域装配），不接受客户端传入；</li>
     * <li>活动不由客户端指定：服务端按当前生效活动与互斥组自行选出，避免客户端拼优惠组合；</li>
     * <li>请求级幂等由外层命令承担 —— 冻结是订单确认事务的一部分，重复确认由
     * {@code ORDER_CONFIRM} 的 {@code Idempotency-Key} 回放首次结果，不会走到这里第二次。
     * 本方法仍保留「一个订单至多一份」的兜底判定，供非幂等路径调用时拒绝而不是覆盖。</li>
     * </ul>
     */
    @Transactional(rollbackFor = Exception.class)
    public PromotionDiscountVO freeze(PromotionOrderFacts facts, Long couponInstanceId) {
        if (orderDiscountDao.selectByOrderId(facts.salesOrderId()) != null) {
            throw new ScmBusinessException(PromotionErrorCode.DISCOUNT_ALREADY_FROZEN);
        }
        List<PromotionDiscountAllocator.Line> lines = facts.lines().stream()
                .map(line -> new PromotionDiscountAllocator.Line(line.orderItemId(), line.baseAmount()))
                .toList();
        PromotionDiscountVO computed = compute(facts.customerId(), null, couponInstanceId, lines,
                facts.salesOrderId());
        if (computed.getDiscountAmount() == null || computed.getDiscountAmount().signum() == 0) {
            // 没有优惠就不落冻结记录：留一条 0 元记录会让「这单有没有优惠」变得要读快照才知道
            return computed;
        }

        String operator = ScmOperator.current();
        OrderDiscountEntity row = new OrderDiscountEntity();
        row.setSalesOrderId(facts.salesOrderId());
        // 主活动列只记第一条产生优惠的活动；叠加生效的其余活动完整落在快照里（见 activitySnapshot）。
        row.setActivityId(computed.getActivityId());
        row.setActivityVersion(computed.getActivityVersion());
        row.setActivitySnapshot(activitySnapshot(computed));
        row.setCouponInstanceId(computed.getCouponInstanceId());
        row.setCouponSnapshot(couponSnapshot(computed));
        row.setBaseAmount(computed.getBaseAmount());
        row.setDiscountAmount(computed.getDiscountAmount());
        row.setAllocations(allocationsJson(computed));
        row.setRoundingTargetItemId(computed.getRoundingTargetItemId());
        row.setCreatedBy(operator);
        orderDiscountDao.insertDiscount(row);

        // 券占用：只有真正下单才占用。占用失败（已被别人用掉）即整笔回滚，
        // 不会出现「优惠已冻结、券却没占上」这种两边不一致的状态。
        if (computed.getCouponInstanceId() != null) {
            PromotionCouponInstanceEntity instance = promotionCouponInstanceDao
                    .lockById(computed.getCouponInstanceId());
            if (instance == null) {
                throw new ScmBusinessException(PromotionErrorCode.COUPON_INSTANCE_NOT_FOUND);
            }
            if (promotionCouponInstanceDao.markReserved(instance.getId(), instance.getVersion(),
                    facts.salesOrderId(), operator) != 1) {
                throw new ScmBusinessException(PromotionErrorCode.COUPON_INSTANCE_UNAVAILABLE);
            }
        }

        computed.setFrozen(true);
        computed.setSalesOrderId(facts.salesOrderId());
        computed.setCreatedAt(row.getCreatedAt());
        computed.setCreatedBy(operator);
        return computed;
    }

    /**
     * 订单已冻结优惠（只读）。
     *
     * <p>
     * 没有冻结记录返回 {@code null} 而不是 0 元对象：「这单没优惠」与「优惠是 0」是两件事，
     * 返回 0 会让页面无法区分，也会让「有没有用过优惠」变得要读快照才知道。
     */
    @Transactional(readOnly = true)
    public OrderDiscountVO getByOrder(Long salesOrderId) {
        OrderDiscountEntity row = orderDiscountDao.selectByOrderId(salesOrderId);
        if (row == null) {
            return null;
        }
        OrderDiscountVO vo = new OrderDiscountVO();
        vo.setSalesOrderId(row.getSalesOrderId());
        vo.setActivityId(row.getActivityId());
        vo.setActivityVersion(row.getActivityVersion());
        vo.setActivitySnapshot(row.getActivitySnapshot());
        vo.setCouponInstanceId(row.getCouponInstanceId());
        vo.setCouponSnapshot(row.getCouponSnapshot());
        vo.setBaseAmount(row.getBaseAmount());
        vo.setDiscountAmount(row.getDiscountAmount());
        vo.setAllocations(parseAllocations(row.getAllocations()));
        vo.setRoundingTargetItemId(row.getRoundingTargetItemId());
        vo.setCreatedAt(row.getCreatedAt());
        vo.setCreatedBy(row.getCreatedBy());
        return vo;
    }

    /** 冻结时写进去的是 JSON 数组文本，这里原样读回，不在读路径上重算分摊。 */
    private List<OrderDiscountVO.Allocation> parseAllocations(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            List<Map<String, Object>> rows = objectMapper.readValue(json,
                    new com.fasterxml.jackson.core.type.TypeReference<List<Map<String, Object>>>() {
                    });
            List<OrderDiscountVO.Allocation> allocations = new ArrayList<>(rows.size());
            for (Map<String, Object> row : rows) {
                OrderDiscountVO.Allocation allocation = new OrderDiscountVO.Allocation();
                allocation.setOrderItemId(longValue(row.get("orderItemId")));
                allocation.setBaseAmount(decimal(row.get("baseAmount")));
                allocation.setDiscountAmount(decimal(row.get("discountAmount")));
                allocations.add(allocation);
            }
            return allocations;
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalStateException("订单优惠分摊反序列化失败", e);
        }
    }

    private static Long longValue(Object value) {
        if (value == null) {
            return null;
        }
        return value instanceof Number number ? number.longValue() : Long.valueOf(String.valueOf(value));
    }

    // ------------------------------------------------------------------
    // 内部
    // ------------------------------------------------------------------

    private PromotionDiscountVO compute(Long customerId, Long activityId, Long couponInstanceId,
            List<PromotionDiscountAllocator.Line> lines, Long salesOrderId) {
        OffsetDateTime now = OffsetDateTime.now();

        PromotionDiscountVO vo = new PromotionDiscountVO();
        vo.setCustomerId(customerId);
        vo.setSalesOrderId(salesOrderId);

        // 1) 活动：先选出真正生效的那几条，再按优先级顺序逐条作用在**剩余金额**上
        List<PromotionActivityEntity> candidates = activityId == null
                ? promotionActivityDao.listActive(now)
                : List.of(requireActiveActivity(activityId, now));
        List<PromotionActivityEntity> applied = selectApplied(candidates, vo);

        BigDecimal baseTotal = lines.stream().map(PromotionDiscountAllocator.Line::baseAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add).setScale(SCALE, RoundingMode.HALF_UP);
        BigDecimal remaining = baseTotal;
        BigDecimal activityDiscount = BigDecimal.ZERO.setScale(SCALE);
        List<PromotionDiscountVO.AppliedActivityVO> appliedActivities = new ArrayList<>();
        for (PromotionActivityEntity activity : applied) {
            BigDecimal delta = activityDiscountOf(activity, remaining);
            if (delta.signum() <= 0) {
                continue;
            }
            activityDiscount = activityDiscount.add(delta);
            remaining = remaining.subtract(delta).max(BigDecimal.ZERO);
            appliedActivities.add(appliedActivity(activity, delta));
            if (vo.getActivityId() == null) {
                // 主活动只记「实际产生优惠的第一条」：多组叠加时，快速展示能说出主规则是哪一条；
                // 叠加生效的其余活动完整落在 appliedActivities 里，冻结时一并入快照。
                vo.setActivityId(activity.getId());
                vo.setActivityCode(activity.getActivityCode());
                vo.setActivityName(activity.getActivityName());
                vo.setActivityVersion(activity.getVersion());
                vo.setActivityRule(activity.getRule());
            }
        }
        vo.setAppliedActivities(appliedActivities);

        // 2) 券：按门槛在「活动后剩余金额」上判定
        BigDecimal couponDiscount = BigDecimal.ZERO.setScale(SCALE);
        PromotionCouponEntity coupon = null;
        PromotionCouponInstanceEntity instance = null;
        if (couponInstanceId != null) {
            instance = requireUsableInstance(couponInstanceId, customerId);
            coupon = requireActiveCoupon(instance.getCouponId(), now);
            couponDiscount = couponDiscountOf(coupon, remaining);
            vo.setCouponInstanceId(instance.getId());
            vo.setCouponCode(coupon.getCouponCode());
            vo.setCouponName(coupon.getCouponName());
        }

        // 3) 合计分摊：活动与券合并成一个总优惠后按行比例分摊，避免两轮分摊各归集一次差额
        BigDecimal total = activityDiscount.add(couponDiscount).min(baseTotal).setScale(SCALE, RoundingMode.HALF_UP);
        PromotionDiscountAllocator.Result allocation = PromotionDiscountAllocator.allocate(lines, total);

        vo.setBaseAmount(baseTotal);
        vo.setActivityDiscount(activityDiscount);
        vo.setCouponDiscount(couponDiscount);
        vo.setDiscountAmount(allocation.discountAmount());
        vo.setRoundingTargetItemId(allocation.roundingTargetItemId());
        vo.setAllocations(allocation.allocations().stream().map(item -> {
            PromotionDiscountVO.PromotionDiscountAllocationVO allocationVo =
                    new PromotionDiscountVO.PromotionDiscountAllocationVO();
            allocationVo.setOrderItemId(item.orderItemId());
            allocationVo.setBaseAmount(item.baseAmount());
            allocationVo.setDiscountAmount(item.discountAmount());
            return allocationVo;
        }).toList());
        return vo;
    }

    /**
     * 互斥组内取优先级最高的一条（并列取 id 最小），其余记入「被挤掉的活动」。
     *
     * <p>
     * 无互斥组（{@code null}）的活动各自独立成组，因此可以叠加。
     */
    private static List<PromotionActivityEntity> selectApplied(List<PromotionActivityEntity> candidates,
            PromotionDiscountVO vo) {
        Map<String, PromotionActivityEntity> winners = new LinkedHashMap<>();
        for (PromotionActivityEntity activity : candidates.stream()
                .sorted(Comparator.comparing(PromotionActivityEntity::getPriority,
                                Comparator.nullsFirst(Comparator.naturalOrder()))
                        .reversed().thenComparing(PromotionActivityEntity::getId))
                .toList()) {
            // 无互斥组时用「自身 id」当组名，等于每组只有自己一条，因此可叠加
            String group = activity.getExclusiveGroup() == null ? "#" + activity.getId()
                    : activity.getExclusiveGroup();
            PromotionActivityEntity existing = winners.get(group);
            if (existing == null) {
                winners.put(group, activity);
            } else {
                vo.getSuppressedActivities().add(activity.getActivityCode());
            }
        }
        return new ArrayList<>(winners.values());
    }

    /**
     * 单条活动作用在剩余金额上的减免额。
     *
     * <p>
     * 满赠的减免额恒为 0：赠品是**非金额**权益，它不该让订单金额变小，也不该参与按行分摊。
     */
    private static BigDecimal activityDiscountOf(PromotionActivityEntity activity, BigDecimal remaining) {
        Map<String, Object> rule = activity.getRule();
        if (rule == null) {
            return BigDecimal.ZERO.setScale(SCALE);
        }
        ScmPromotionActivityTypeEnum type = ScmPromotionActivityTypeEnum.valueOf(activity.getActivityType());
        BigDecimal threshold = decimal(rule.get("thresholdAmount"));
        return switch (type) {
            case FULL_REDUCE -> threshold != null && remaining.compareTo(threshold) >= 0
                    ? min(decimal(rule.get("reduceAmount")), remaining) : BigDecimal.ZERO.setScale(SCALE);
            case DISCOUNT -> {
                BigDecimal rate = decimal(rule.get("discountRate"));
                yield rate == null ? BigDecimal.ZERO.setScale(SCALE)
                        : remaining.multiply(BigDecimal.ONE.subtract(rate)).setScale(SCALE, RoundingMode.HALF_UP);
            }
            case FULL_GIFT -> BigDecimal.ZERO.setScale(SCALE);
        };
    }

    private static BigDecimal couponDiscountOf(PromotionCouponEntity coupon, BigDecimal remaining) {
        if (coupon.getMinOrderAmount() != null && remaining.compareTo(coupon.getMinOrderAmount()) < 0) {
            throw new ScmBusinessException(PromotionErrorCode.THRESHOLD_NOT_REACHED);
        }
        ScmPromotionCouponDiscountTypeEnum type = ScmPromotionCouponDiscountTypeEnum
                .valueOf(coupon.getDiscountType());
        return switch (type) {
            case AMOUNT -> min(coupon.getDiscountValue(), remaining);
            case RATE -> remaining.multiply(BigDecimal.ONE.subtract(coupon.getDiscountValue()))
                    .setScale(SCALE, RoundingMode.HALF_UP);
        };
    }

    private PromotionActivityEntity requireActiveActivity(Long id, OffsetDateTime now) {
        PromotionActivityEntity row = promotionActivityDao.selectById(id);
        if (row == null || Boolean.TRUE.equals(row.getDeleted())) {
            throw new ScmBusinessException(PromotionErrorCode.ACTIVITY_NOT_FOUND);
        }
        if (!ScmPromotionStatusEnum.ACTIVE.name().equals(row.getStatus()) || !row.getValidFrom().isBefore(now)
                || !row.getValidTo().isAfter(now)) {
            throw new ScmBusinessException(PromotionErrorCode.ACTIVITY_STATE_INVALID);
        }
        return row;
    }

    private PromotionCouponEntity requireActiveCoupon(Long couponId, OffsetDateTime now) {
        PromotionCouponEntity row = promotionCouponDao.selectById(couponId);
        if (row == null || Boolean.TRUE.equals(row.getDeleted())) {
            throw new ScmBusinessException(PromotionErrorCode.COUPON_NOT_FOUND);
        }
        if (!ScmPromotionStatusEnum.ACTIVE.name().equals(row.getStatus()) || !row.getValidFrom().isBefore(now)
                || !row.getValidTo().isAfter(now)) {
            throw new ScmBusinessException(PromotionErrorCode.COUPON_STATE_INVALID);
        }
        return row;
    }

    /**
     * 券必须属于该客户且状态为 AVAILABLE：不接受「借别人的券」或「已用过的券」。
     */
    private PromotionCouponInstanceEntity requireUsableInstance(Long instanceId, Long customerId) {
        PromotionCouponInstanceEntity instance = promotionCouponInstanceDao.selectById(instanceId);
        if (instance == null) {
            throw new ScmBusinessException(PromotionErrorCode.COUPON_INSTANCE_NOT_FOUND);
        }
        if (!Objects.equals(instance.getCustomerId(), customerId)) {
            throw new ScmBusinessException(PromotionErrorCode.COUPON_INSTANCE_NOT_FOUND);
        }
        if (!ScmPromotionCouponInstanceStatusEnum.AVAILABLE.name().equals(instance.getStatus())) {
            throw new ScmBusinessException(PromotionErrorCode.COUPON_INSTANCE_STATE_INVALID);
        }
        return instance;
    }

    private Map<String, Object> couponSnapshot(PromotionDiscountVO vo) {
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
     * 活动快照：冻结**每一条实际生效活动**及其贡献额，外加被互斥组挤掉的活动。
     *
     * <p>
     * 为什么不直接存 {@code activityRule}（单条规则）：不同互斥组可以叠加，真实生效的可能不止一条。
     * 只冻结主规则会让退款反向漏掉叠加的那部分，而快照是不可变的，事后无法补算。
     *
     * <p>
     * 形状保持 {@code object}（{@code order_discount.activity_snapshot} 有
     * {@code jsonb_typeof = 'object'} 约束），因此不需要改表：完整列表放在 {@code applied} 键下。
     */
    private Map<String, Object> activitySnapshot(PromotionDiscountVO vo) {
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

    private static PromotionDiscountVO.AppliedActivityVO appliedActivity(PromotionActivityEntity activity,
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

    private String allocationsJson(PromotionDiscountVO vo) {
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
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalStateException("订单优惠分摊序列化失败", e);
        }
    }

    private static BigDecimal min(BigDecimal value, BigDecimal cap) {
        if (value == null) {
            return BigDecimal.ZERO.setScale(SCALE);
        }
        return value.min(cap).max(BigDecimal.ZERO).setScale(SCALE, RoundingMode.HALF_UP);
    }

    private static BigDecimal amount(BigDecimal value) {
        return value == null ? BigDecimal.ZERO.setScale(SCALE) : value.setScale(SCALE, RoundingMode.HALF_UP);
    }

    private static BigDecimal decimal(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return new BigDecimal(String.valueOf(value).trim());
        } catch (NumberFormatException e) {
            throw new ScmBusinessException(PromotionErrorCode.RULE_INVALID);
        }
    }

    private static String plain(BigDecimal value) {
        return value == null ? null : value.toPlainString();
    }
}
