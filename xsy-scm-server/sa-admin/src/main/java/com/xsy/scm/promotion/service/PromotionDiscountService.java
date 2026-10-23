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
import com.xsy.scm.promotion.dao.OrderPromotionGiftDao;
import com.xsy.scm.promotion.dao.PromotionActivityDao;
import com.xsy.scm.promotion.dao.PromotionCouponDao;
import com.xsy.scm.promotion.dao.PromotionCouponInstanceDao;
import com.xsy.scm.promotion.domain.entity.OrderDiscountEntity;
import com.xsy.scm.promotion.domain.entity.OrderPromotionGiftEntity;
import com.xsy.scm.promotion.domain.entity.PromotionActivityEntity;
import com.xsy.scm.promotion.domain.entity.PromotionCouponEntity;
import com.xsy.scm.promotion.domain.entity.PromotionCouponInstanceEntity;
import com.xsy.scm.promotion.domain.form.PromotionDiscountPreviewForm;
import com.xsy.scm.promotion.domain.vo.OrderDiscountVO;
import com.xsy.scm.promotion.domain.vo.PromotionDiscountVO;
import com.xsy.scm.promotion.support.PromotionDiscountAllocator;
import com.xsy.scm.promotion.support.PromotionGiftFact;
import com.xsy.scm.promotion.support.PromotionOrderFacts;
import com.xsy.scm.product.dao.ProductSkuOptionDao;
import com.xsy.scm.product.domain.vo.ProductSkuOptionVO;

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
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import static com.xsy.scm.promotion.service.PromotionDiscountSnapshotMapper.activitySnapshot;
import static com.xsy.scm.promotion.service.PromotionDiscountSnapshotMapper.allocationsJson;
import static com.xsy.scm.promotion.service.PromotionDiscountSnapshotMapper.appliedActivity;
import static com.xsy.scm.promotion.service.PromotionDiscountSnapshotMapper.couponSnapshot;

/**
 * 订单优惠先试算，确认订单时重新校验并保存结果。互斥活动按优先级选择，其他活动按既定顺序计算。
 */
@Service
@RequiredArgsConstructor
public class PromotionDiscountService {

    private static final int SCALE = 4;

    private final PromotionActivityDao promotionActivityDao;

    private final PromotionCouponDao promotionCouponDao;

    private final PromotionCouponInstanceDao promotionCouponInstanceDao;

    private final OrderDiscountDao orderDiscountDao;

    private final OrderPromotionGiftDao orderPromotionGiftDao;

    /**
     * 跨域只读：满赠保存时取赠品 SKU 的名称 / 规格 / 销售单位。只读商品主档，不写商品域的表（见 {@code cross-domain-dao-allowlist.tsv}）。
     */
    private final ProductSkuOptionDao productSkuOptionDao;

    private final ObjectMapper objectMapper;

    /**
     * 试算：只读，不占用券。
     *
     * <p>
     * 试算面向还没有订单的结算预览，客户与行由调用方给出，不写任何表。
     */
    @Transactional(readOnly = true)
    public PromotionDiscountVO preview(PromotionDiscountPreviewForm form) {
        List<PromotionOrderFacts.Line> lines = form.getLines().stream()
            .map(line -> new PromotionOrderFacts.Line(line.getOrderItemId(), line.getSkuId(),
                amount(line.getQuantity()), amount(line.getBaseAmount())))
            .toList();
        return compute(form.getCustomerId(), form.getActivityId(), form.getCouponInstanceId(), lines, null);
    }

    /**
     * 订单优惠在确认订单时由服务端计算并保存。客户、金额取真实订单，活动由服务端选择；重复确认不得新增第二套优惠记录。
     */
    @Transactional(rollbackFor = Exception.class)
    public PromotionDiscountVO freeze(PromotionOrderFacts facts, Long couponInstanceId) {
        if (orderDiscountDao.selectByOrderId(facts.salesOrderId()) != null) {
            throw new ScmBusinessException(PromotionErrorCode.DISCOUNT_ALREADY_FROZEN);
        }
        PromotionDiscountVO computed = compute(facts.customerId(), null, couponInstanceId, facts.lines(),
            facts.salesOrderId());
        boolean hasDiscount = computed.getDiscountAmount() != null && computed.getDiscountAmount().signum() > 0;
        boolean hasGifts = computed.getGifts() != null && !computed.getGifts().isEmpty();
        if (!hasDiscount && !hasGifts) {
            // 没有金额优惠也没有赠品就不写记录：0 元记录会让「有没有优惠」得读内容才知道。
            // 只有赠品的单也要保存：赠品不减金额，但出库、分拣和成本都靠它。
            return computed;
        }

        String operator = ScmOperator.current();
        if (hasDiscount) {
            OrderDiscountEntity row = new OrderDiscountEntity();
            row.setSalesOrderId(facts.salesOrderId());
            // 主活动列只记第一条产生优惠的活动；叠加生效的其余活动完整保存在 activitySnapshot 里。
            row.setActivityId(computed.getActivityId());
            row.setActivityVersion(computed.getActivityVersion());
            row.setActivitySnapshot(activitySnapshot(computed));
            row.setCouponInstanceId(computed.getCouponInstanceId());
            row.setCouponSnapshot(couponSnapshot(computed));
            row.setBaseAmount(computed.getBaseAmount());
            row.setDiscountAmount(computed.getDiscountAmount());
            row.setSpecialDiscountAmount(computed.getSpecialDiscount());
            row.setAllocations(allocationsJson(objectMapper, computed));
            row.setRoundingTargetItemId(computed.getRoundingTargetItemId());
            row.setCreatedBy(operator);
            orderDiscountDao.insertDiscount(row);
        }
        for (PromotionDiscountVO.GiftEntitlementVO gift : computed.getGifts()) {
            orderPromotionGiftDao.insertGift(giftEntity(facts.salesOrderId(), gift, operator));
        }

        // 券只在真正下单时占用；占用失败（已被别人用掉）就整笔回滚，
        // 不会出现「优惠已保存、券没占上」的状态。
        if (computed.getCouponInstanceId() != null) {
            PromotionCouponInstanceEntity instance = promotionCouponInstanceDao
                .lockById(computed.getCouponInstanceId());
            if (instance == null) {
                throw new ScmBusinessException(PromotionErrorCode.COUPON_INSTANCE_NOT_FOUND);
            }
            if (promotionCouponInstanceDao.markReserved(instance.getId(), instance.getVersion(), facts.salesOrderId(),
                operator) != 1) {
                throw new ScmBusinessException(PromotionErrorCode.COUPON_INSTANCE_UNAVAILABLE);
            }
        }

        computed.setFrozen(true);
        computed.setSalesOrderId(facts.salesOrderId());
        computed.setCreatedAt(OffsetDateTime.now());
        computed.setCreatedBy(operator);
        return computed;
    }

    /**
     * 订单保存的赠品权益，只读。
     *
     * <p>
     * 与优惠分开读：只有赠品、没有金额优惠的订单没有 {@code xsy_order_discount} 行， 塞进优惠模型会让这种订单看起来什么都没有。
     */
    @Transactional(readOnly = true)
    public List<PromotionDiscountVO.GiftEntitlementVO> listGifts(Long salesOrderId) {
        return orderPromotionGiftDao.listByOrder(salesOrderId).stream().map(PromotionDiscountService::toGiftVO)
            .toList();
    }

    /**
     * 订单保存的赠品权益数据（出库用）：只给「哪一条权益、哪个 SKU、多少」。
     *
     * <p>
     * 与 {@link #listGifts} 分开：出库只关心权益、SKU 和数量，不需要活动名与规则。
     */
    @Transactional(readOnly = true)
    public List<PromotionGiftFact> listGiftFacts(Long salesOrderId) {
        return orderPromotionGiftDao.listByOrder(salesOrderId).stream()
            .map(row -> new PromotionGiftFact(row.getId(), row.getSalesOrderId(), row.getSkuId(), row.getQuantity()))
            .toList();
    }

    private static PromotionDiscountVO.GiftEntitlementVO toGiftVO(OrderPromotionGiftEntity row) {
        PromotionDiscountVO.GiftEntitlementVO vo = new PromotionDiscountVO.GiftEntitlementVO();
        vo.setActivityId(row.getActivityId());
        vo.setVersion(row.getActivityVersion());
        vo.setSkuId(row.getSkuId());
        vo.setSkuCode(row.getSkuCodeSnapshot());
        vo.setProductName(row.getProductNameSnapshot());
        vo.setSpecName(row.getSpecNameSnapshot());
        vo.setSaleUnit(row.getSaleUnitSnapshot());
        vo.setQuantity(row.getQuantity());
        vo.setRule(row.getRuleSnapshot());
        return vo;
    }

    private static OrderPromotionGiftEntity giftEntity(Long salesOrderId, PromotionDiscountVO.GiftEntitlementVO gift,
        String operator) {
        OrderPromotionGiftEntity row = new OrderPromotionGiftEntity();
        row.setSalesOrderId(salesOrderId);
        row.setActivityId(gift.getActivityId());
        row.setActivityVersion(gift.getVersion());
        row.setSkuId(gift.getSkuId());
        row.setSkuCodeSnapshot(gift.getSkuCode());
        row.setProductNameSnapshot(gift.getProductName());
        row.setSpecNameSnapshot(gift.getSpecName());
        row.setSaleUnitSnapshot(gift.getSaleUnit());
        row.setQuantity(gift.getQuantity());
        row.setRuleSnapshot(gift.getRule());
        row.setCreatedBy(operator);
        return row;
    }

    /**
     * 优惠券在正常签收时从占用变成已使用，与签收及应收生成同事务。异常签收不核销，签收后退款也不重新激活已使用券。
     */
    @Transactional(propagation = Propagation.MANDATORY, rollbackFor = Exception.class)
    public void markCouponUsed(Long salesOrderId) {
        OrderDiscountEntity row = orderDiscountDao.selectByOrderId(salesOrderId);
        if (row == null || row.getCouponInstanceId() == null) {
            return;
        }
        PromotionCouponInstanceEntity instance = promotionCouponInstanceDao.lockById(row.getCouponInstanceId());
        if (instance == null) {
            throw new ScmBusinessException(PromotionErrorCode.COUPON_INSTANCE_NOT_FOUND);
        }
        if (ScmPromotionCouponInstanceStatusEnum.USED.name().equals(instance.getStatus())) {
            // 幂等：签收重放（或并发下的重复触发）不该报错，券已经在这个订单上用掉了。
            return;
        }
        if (promotionCouponInstanceDao.markUsed(instance.getId(), instance.getVersion(), salesOrderId,
            ScmOperator.current()) != 1) {
            // 券不在 RESERVED（可能已被释放）：宁可失败，也不留下一张状态说不清的券。
            throw new ScmBusinessException(PromotionErrorCode.COUPON_INSTANCE_STATE_INVALID);
        }
    }

    /**
     * 订单已保存的优惠，只读。
     *
     * <p>
     * 没有记录返回 {@code null} 而不是 0 元对象：「没优惠」和「优惠为 0」是两件事，页面需要区分。
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
        vo.setSpecialDiscountAmount(row.getSpecialDiscountAmount());
        vo.setAllocations(parseAllocations(row.getAllocations()));
        vo.setRoundingTargetItemId(row.getRoundingTargetItemId());
        vo.setCreatedAt(row.getCreatedAt());
        vo.setCreatedBy(row.getCreatedBy());
        return vo;
    }

    /** 保存时写入的是 JSON 数组文本，这里原样读回，读取时不重算分摊。 */
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

    // 内部

    private PromotionDiscountVO compute(Long customerId, Long activityId, Long couponInstanceId,
        List<PromotionOrderFacts.Line> lines, Long salesOrderId) {
        OffsetDateTime now = OffsetDateTime.now();

        PromotionDiscountVO vo = new PromotionDiscountVO();
        vo.setCustomerId(customerId);
        vo.setSalesOrderId(salesOrderId);

        // 1) 活动：先选出真正生效的那几条，再按优先级顺序作用
        List<PromotionActivityEntity> candidates = activityId == null
            ? promotionActivityDao.listActive(now)
            : List.of(requireActiveActivity(activityId, now));
        List<PromotionActivityEntity> applied = selectApplied(candidates, vo);

        BigDecimal baseTotal = lines.stream().map(line -> amount(line.baseAmount()))
            .reduce(BigDecimal.ZERO, BigDecimal::add).setScale(SCALE, RoundingMode.HALF_UP);
        List<PromotionDiscountVO.AppliedActivityVO> appliedActivities = new ArrayList<>();
        List<PromotionDiscountVO.GiftEntitlementVO> gifts = new ArrayList<>();

        // 1a) 限时特价：作用在基础价之上、其余活动之前（基础价 → 限时特价 → 满减/折扣 → 券）。
        // 让利按行归集：特价针对某个 SKU，摊到别的行上会让退款反向错行。
        Map<Long, BigDecimal> specialByItem = new LinkedHashMap<>();
        for (PromotionActivityEntity activity : applied) {
            if (!ScmPromotionActivityTypeEnum.SPECIAL_PRICE.name().equals(activity.getActivityType())) {
                continue;
            }
            BigDecimal delta = specialPriceDelta(activity, lines, specialByItem);
            if (delta.signum() > 0) {
                appliedActivities.add(appliedActivity(activity, delta));
                markPrimaryActivity(vo, activity);
            }
        }
        BigDecimal specialDiscount = specialByItem.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add)
            .setScale(SCALE, RoundingMode.HALF_UP).min(baseTotal);
        BigDecimal remaining = baseTotal.subtract(specialDiscount).max(BigDecimal.ZERO);

        // 1b) 满减 / 折扣 / 满赠：逐条作用在特价之后的剩余金额上
        BigDecimal activityDiscount = BigDecimal.ZERO.setScale(SCALE);
        for (PromotionActivityEntity activity : applied) {
            if (ScmPromotionActivityTypeEnum.FULL_GIFT.name().equals(activity.getActivityType())) {
                // 满赠不减少订单金额，不参与剩余金额递减，也不进 appliedActivities。
                // 门槛按基础合计判定：同单另有满减不该把门槛压没。
                if (giftThresholdReached(activity, baseTotal)) {
                    gifts.add(giftEntitlement(activity));
                }
                continue;
            }
            if (ScmPromotionActivityTypeEnum.SPECIAL_PRICE.name().equals(activity.getActivityType())) {
                // 特价已在 1a 处理：它不是「作用在剩余金额上」的订单级优惠
                continue;
            }
            BigDecimal delta = activityDiscountOf(activity, remaining);
            if (delta.signum() <= 0) {
                continue;
            }
            activityDiscount = activityDiscount.add(delta);
            remaining = remaining.subtract(delta).max(BigDecimal.ZERO);
            appliedActivities.add(appliedActivity(activity, delta));
            markPrimaryActivity(vo, activity);
        }
        vo.setAppliedActivities(appliedActivities);
        vo.setGifts(gifts);

        // 2) 券：按门槛在「活动后剩余金额」上判定
        BigDecimal couponDiscount = BigDecimal.ZERO.setScale(SCALE);
        if (couponInstanceId != null) {
            PromotionCouponInstanceEntity instance = requireUsableInstance(couponInstanceId, customerId);
            PromotionCouponEntity coupon = requireActiveCoupon(instance.getCouponId(), now);
            couponDiscount = couponDiscountOf(coupon, remaining);
            vo.setCouponInstanceId(instance.getId());
            vo.setCouponCode(coupon.getCouponCode());
            vo.setCouponName(coupon.getCouponName());
        }

        // 3) 分摊：满减/折扣与券合成一个总额，在特价之后的行金额上按比例分摊（优惠跟着客户实际要付的钱走），
        // 特价让利再逐行加回。两段相加即逐行优惠，且每行不会超过行金额。
        BigDecimal proportional = activityDiscount.add(couponDiscount).min(remaining).setScale(SCALE,
            RoundingMode.HALF_UP);
        List<PromotionDiscountAllocator.Line> adjusted = lines.stream()
            .map(line -> new PromotionDiscountAllocator.Line(line.orderItemId(),
                amount(line.baseAmount())
                    .subtract(specialByItem.getOrDefault(line.orderItemId(), BigDecimal.ZERO.setScale(SCALE)))))
            .toList();
        PromotionDiscountAllocator.Result allocation = PromotionDiscountAllocator.allocate(adjusted, proportional);
        Map<Long, BigDecimal> proportionalByItem = new LinkedHashMap<>();
        for (PromotionDiscountAllocator.Allocation item : allocation.allocations()) {
            proportionalByItem.put(item.orderItemId(), item.discountAmount());
        }

        BigDecimal total = specialDiscount.add(allocation.discountAmount()).min(baseTotal).setScale(SCALE,
            RoundingMode.HALF_UP);
        vo.setBaseAmount(baseTotal);
        vo.setSpecialDiscount(specialDiscount);
        vo.setActivityDiscount(activityDiscount);
        vo.setCouponDiscount(couponDiscount);
        vo.setDiscountAmount(total);
        vo.setFinalAmount(baseTotal.subtract(total).max(BigDecimal.ZERO));
        vo.setRoundingTargetItemId(allocation.roundingTargetItemId());
        // 逐行基础金额回填原始行金额（不是特价后的），否则「原价多少」在分摊里就丢了
        vo.setAllocations(lines.stream().map(line -> {
            var allocationVo = new PromotionDiscountVO.PromotionDiscountAllocationVO();
            allocationVo.setOrderItemId(line.orderItemId());
            allocationVo.setBaseAmount(amount(line.baseAmount()));
            BigDecimal defaultAmount = BigDecimal.ZERO.setScale(SCALE);
            BigDecimal specialAmount = specialByItem.getOrDefault(line.orderItemId(), defaultAmount);
            BigDecimal proportionalAmount = proportionalByItem.getOrDefault(line.orderItemId(), defaultAmount);
            allocationVo.setDiscountAmount(specialAmount.add(proportionalAmount).setScale(SCALE, RoundingMode.HALF_UP));
            return allocationVo;
        }).toList());
        return vo;
    }

    /**
     * 主活动只记第一条产生优惠的活动，用于快速展示；其余叠加活动记在 {@code appliedActivities} 里，保存时一并写入。
     */
    private static void markPrimaryActivity(PromotionDiscountVO vo, PromotionActivityEntity activity) {
        if (vo.getActivityId() != null) {
            return;
        }
        vo.setActivityId(activity.getId());
        vo.setActivityCode(activity.getActivityCode());
        vo.setActivityName(activity.getActivityName());
        vo.setActivityVersion(activity.getVersion());
        vo.setActivityRule(activity.getRule());
    }

    /**
     * 限时特价只在低于基础价时产生让利，同一商品行只应用一次。让利按商品行记录，不分摊。
     */
    private static BigDecimal specialPriceDelta(PromotionActivityEntity activity, List<PromotionOrderFacts.Line> lines,
        Map<Long, BigDecimal> specialByItem) {
        Map<String, Object> rule = activity.getRule();
        Long skuId = rule == null ? null : longValue(rule.get("skuId"));
        BigDecimal specialPrice = rule == null ? null : decimal(rule.get("specialPrice"));
        if (skuId == null || specialPrice == null || specialPrice.signum() <= 0) {
            return BigDecimal.ZERO.setScale(SCALE);
        }
        BigDecimal delta = BigDecimal.ZERO.setScale(SCALE);
        for (PromotionOrderFacts.Line line : lines) {
            if (!skuId.equals(line.skuId()) || specialByItem.containsKey(line.orderItemId())) {
                continue;
            }
            BigDecimal base = amount(line.baseAmount());
            BigDecimal discounted = amount(line.quantity()).multiply(specialPrice).setScale(SCALE,
                RoundingMode.HALF_UP);
            BigDecimal lineDelta = base.subtract(discounted).max(BigDecimal.ZERO).min(base);
            if (lineDelta.signum() > 0) {
                specialByItem.put(line.orderItemId(), lineDelta);
                delta = delta.add(lineDelta);
            }
        }
        return delta;
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
            .sorted(Comparator
                .comparing(PromotionActivityEntity::getPriority, Comparator.nullsFirst(Comparator.naturalOrder()))
                .reversed().thenComparing(PromotionActivityEntity::getId))
            .toList()) {
            // 无互斥组时用「自身 id」当组名，等于每组只有自己一条，因此可叠加
            String group = activity.getExclusiveGroup() == null ? "#" + activity.getId() : activity.getExclusiveGroup();
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
     * 满赠的减免额恒为 0：赠品不减订单金额，也不参与按行分摊。
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
                ? min(decimal(rule.get("reduceAmount")), remaining)
                : BigDecimal.ZERO.setScale(SCALE);
            case DISCOUNT -> {
                BigDecimal rate = decimal(rule.get("discountRate"));
                yield rate == null
                    ? BigDecimal.ZERO.setScale(SCALE)
                    : remaining.multiply(BigDecimal.ONE.subtract(rate)).setScale(SCALE, RoundingMode.HALF_UP);
            }
            case FULL_GIFT -> BigDecimal.ZERO.setScale(SCALE);
            // 特价在 1a 按行归集、调用方已跳过它；这一格只为 switch 穷尽，不构成订单级减免
            case SPECIAL_PRICE -> BigDecimal.ZERO.setScale(SCALE);
        };
    }

    private static BigDecimal couponDiscountOf(PromotionCouponEntity coupon, BigDecimal remaining) {
        if (coupon.getMinOrderAmount() != null && remaining.compareTo(coupon.getMinOrderAmount()) < 0) {
            throw new ScmBusinessException(PromotionErrorCode.THRESHOLD_NOT_REACHED);
        }
        ScmPromotionCouponDiscountTypeEnum type = ScmPromotionCouponDiscountTypeEnum.valueOf(coupon.getDiscountType());
        return switch (type) {
            case AMOUNT -> min(coupon.getDiscountValue(), remaining);
            case RATE -> remaining.multiply(BigDecimal.ONE.subtract(coupon.getDiscountValue())).setScale(SCALE,
                RoundingMode.HALF_UP);
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

    /**
     * 满赠门槛判定：订单金额达到门槛即成立。
     *
     * <p>
     * 门槛比的是基础合计（未扣任何优惠），不是逐条作用后的剩余金额；同单另有满减不该把门槛压没。
     */
    private static boolean giftThresholdReached(PromotionActivityEntity activity, BigDecimal baseTotal) {
        Map<String, Object> rule = activity.getRule();
        if (rule == null) {
            return false;
        }
        BigDecimal threshold = decimal(rule.get("thresholdAmount"));
        return threshold != null && baseTotal.compareTo(threshold) >= 0;
    }

    /**
     * 把满赠规则解析成一条赠品权益，并取赠品 SKU 的名称等信息。
     *
     * <p>
     * 赠品 SKU 读不到就直接失败：保存一条发不出货的权益，比确认订单时当场报错更糟。
     */
    private PromotionDiscountVO.GiftEntitlementVO giftEntitlement(PromotionActivityEntity activity) {
        Map<String, Object> rule = activity.getRule();
        Long skuId = rule == null ? null : longValue(rule.get("giftSkuId"));
        BigDecimal quantity = rule == null ? null : decimal(rule.get("giftQuantity"));
        if (skuId == null || quantity == null || quantity.signum() <= 0) {
            throw new ScmBusinessException(PromotionErrorCode.RULE_INVALID);
        }
        ProductSkuOptionVO sku = productSkuOptionDao.selectByIds(List.of(skuId)).stream().findFirst().orElse(null);
        if (sku == null) {
            throw new ScmBusinessException(PromotionErrorCode.RULE_INVALID);
        }

        PromotionDiscountVO.GiftEntitlementVO gift = new PromotionDiscountVO.GiftEntitlementVO();
        gift.setActivityId(activity.getId());
        gift.setActivityCode(activity.getActivityCode());
        gift.setActivityName(activity.getActivityName());
        gift.setVersion(activity.getVersion());
        gift.setSkuId(sku.getSkuId());
        gift.setSkuCode(sku.getSkuCode());
        gift.setProductName(sku.getProductName());
        gift.setSpecName(sku.getSpecName());
        gift.setSaleUnit(sku.getSaleUnit());
        gift.setQuantity(quantity.setScale(SCALE, RoundingMode.HALF_UP));
        gift.setRule(rule);
        return gift;
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
}
