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
 * 优惠计算：试算（只读）与冻结（占用券 + 落库）。
 *
 * <p>
 * 分工与边界：
 * <ul>
 * <li><b>基础价不在本域</b>：订单行只传「行 id + 基础金额」，基础定价顺序 （协议价 → 客户类型价 → 市场价）仍只有订单域一处实现，活动价在它之后计算。</li>
 * <li><b>试算不占用</b>：{@code preview} 不写任何表、不动券状态（ADR-009：预览不等于最终占用）。</li>
 * <li><b>冻结只认订单事实</b>：{@code freeze} 由订单确认在服务端编排调用，客户与行金额来自
 * {@link PromotionOrderFacts}，活动由服务端自行选出；客户端无法指定客户、行金额或优惠组合。</li>
 * <li><b>冻结时重验</b>：{@code freeze} 重新读活动与券的**当前**状态与版本， 试算之后活动可能已停用、券可能已被别人占用 —— 用试算结果直接落库会写出一个从未成立过的优惠。</li>
 * <li><b>互斥组</b>：同组内只取优先级最高的一条，其余记入 {@code suppressedActivities} 供解释； 不同组可以叠加，顺序按优先级降序、id 升序，保证同一份输入得到同一份结果。</li>
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

    private final OrderPromotionGiftDao orderPromotionGiftDao;

    /**
     * 跨域只读：满赠冻结时取赠品 SKU 的名称 / 规格 / 销售单位快照。 只读商品主档，不写商品域任何表（见 {@code cross-domain-dao-allowlist.tsv}）。
     */
    private final ProductSkuOptionDao productSkuOptionDao;

    private final ObjectMapper objectMapper;

    /**
     * 试算：只读，不占用券。
     *
     * <p>
     * 试算面向「还没有订单」的结算预览，因此客户与行由调用方给出；它不写任何表， 因此不构成「客户端自行组合优惠」——真正落库的冻结只认订单事实。
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
     * 冻结：由**订单确认**在服务端编排调用，按订单事实计算并占用券。
     *
     * <p>
     * 与试算的三点差别：
     * <ul>
     * <li>客户与行金额来自 {@link PromotionOrderFacts}（订单域装配），不接受客户端传入；</li>
     * <li>活动不由客户端指定：服务端按当前生效活动与互斥组自行选出，避免客户端拼优惠组合；</li>
     * <li>请求级幂等由外层命令承担 —— 冻结是订单确认事务的一部分，重复确认由 {@code ORDER_CONFIRM} 的 {@code Idempotency-Key} 回放首次结果，不会走到这里第二次。
     * 本方法仍保留「一个订单至多一份」的兜底判定，供非幂等路径调用时拒绝而不是覆盖。</li>
     * </ul>
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
            // 既没有金额优惠也没有赠品就不落冻结记录：留一条 0 元记录会让「这单有没有优惠」
            // 变得要读快照才知道。反之，**只有赠品的单也必须冻结** —— 赠品是独立事实，
            // 它不产生金额优惠，但出库、分拣与成本都靠它。
            return computed;
        }

        String operator = ScmOperator.current();
        if (hasDiscount) {
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
            row.setSpecialDiscountAmount(computed.getSpecialDiscount());
            row.setAllocations(allocationsJson(objectMapper, computed));
            row.setRoundingTargetItemId(computed.getRoundingTargetItemId());
            row.setCreatedBy(operator);
            orderDiscountDao.insertDiscount(row);
        }
        for (PromotionDiscountVO.GiftEntitlementVO gift : computed.getGifts()) {
            orderPromotionGiftDao.insertGift(giftEntity(facts.salesOrderId(), gift, operator));
        }

        // 券占用：只有真正下单才占用。占用失败（已被别人用掉）即整笔回滚，
        // 不会出现「优惠已冻结、券却没占上」这种两边不一致的状态。
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
     * 订单冻结的赠品权益（只读）。
     *
     * <p>
     * 与优惠分开读：赠品是**非金额权益**，只有赠品、没有金额优惠的订单不会有 {@code order_discount} 行， 把赠品塞进优惠读模型会让这种订单看起来「什么都没有」。
     */
    @Transactional(readOnly = true)
    public List<PromotionDiscountVO.GiftEntitlementVO> listGifts(Long salesOrderId) {
        return orderPromotionGiftDao.listByOrder(salesOrderId).stream().map(PromotionDiscountService::toGiftVO)
                .toList();
    }

    /**
     * 订单冻结的赠品权益事实（出库用）：只给「哪一行权益、哪个 SKU、多少」。
     *
     * <p>
     * 与 {@link #listGifts} 分开：出库不关心活动名与规则，而订单详情需要解释「为什么送」。 一个方法返回两套字段只会让出库路径被迫依赖展示模型。
     */
    @Transactional(readOnly = true)
    public List<PromotionGiftFact> listGiftFacts(Long salesOrderId) {
        return orderPromotionGiftDao.listByOrder(salesOrderId).stream().map(
                row -> new PromotionGiftFact(row.getId(), row.getSalesOrderId(), row.getSkuId(), row.getQuantity()))
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
     * 正常签收 → 核销该订单占用的券：{@code RESERVED → USED}。
     *
     * <p>
     * <b>必须与签收同事务</b>（{@code MANDATORY}）：签收是订单级不可逆终态，也正是应收的形成时点， 券「已被一笔真实成交用掉」的判定与它对齐。任何一步失败整笔回滚，不会出现 「已签收但券还是
     * RESERVED」，也不会出现「券已核销但财务没生成」。
     *
     * <p>
     * <b>异常签收不核销</b>：{@code EXCEPTION} 不形成应收，券也保持 {@code RESERVED}， 等异常解决后真正签收再核销 —— 与「异常签收不形成应收」同一条规则。
     *
     * <p>
     * <b>退款不恢复券</b>：签收之后的部分 / 全额退款都保持 {@code USED}。退款反向的是**金额** （按冻结分摊），券回答的是**权益是否已被一笔成交使用过**，两者不是一回事； 允许复活会让「100 元订单用
     * 20 元券、退款后券回来、再用于下一单」变成重复营销权益。 业务若确需「全额退款返券」，应重新 {@code issue} 一张新券，而不是把旧券改回 {@code AVAILABLE}。
     *
     * <p>
     * 没有冻结记录或没用券时成功跳过：签收本身合法，不能因为没有券而回滚。
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
            // 券不在 RESERVED（例如未来「撤销确认」把它释放了）：宁可失败，也不写出一张状态说不清的券。
            throw new ScmBusinessException(PromotionErrorCode.COUPON_INSTANCE_STATE_INVALID);
        }
    }

    /**
     * 订单已冻结优惠（只读）。
     *
     * <p>
     * 没有冻结记录返回 {@code null} 而不是 0 元对象：「这单没优惠」与「优惠是 0」是两件事， 返回 0 会让页面无法区分，也会让「有没有用过优惠」变得要读快照才知道。
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

        // 1a) 限时特价：作用在**基础价之上、其余活动之前**（基础价 → 限时特价 → 满减/折扣 → 券）。
        // 让利按**行**归集而不是按金额比例分摊：特价针对某个 SKU，摊到别的行上会让退款反向错行。
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

        // 1b) 满减 / 折扣 / 满赠：逐条作用在**特价之后**的剩余金额上
        BigDecimal activityDiscount = BigDecimal.ZERO.setScale(SCALE);
        for (PromotionActivityEntity activity : applied) {
            if (ScmPromotionActivityTypeEnum.FULL_GIFT.name().equals(activity.getActivityType())) {
                // 满赠是**非金额权益**：它不让订单金额变小，所以既不参与下面的剩余金额递减，
                // 也不进 appliedActivities（那里记的是「实际减了多少钱的活动」）。
                // 门槛按**基础合计**判定，不按逐条作用后的剩余金额 —— 「满 100 赠 2kg」不该因为
                // 同单另有满减把门槛压没：客户看的是订单金额达没达标。
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

        // 3) 分摊：满减/折扣与券合成一个总额，在**特价之后**的行金额上按比例分摊（这样优惠跟着
        // 客户实际要付的钱走）；特价让利再逐行加回。两段相加即逐行优惠，且每行不会超过行金额。
        BigDecimal proportional = activityDiscount.add(couponDiscount).min(remaining).setScale(SCALE,
                RoundingMode.HALF_UP);
        List<PromotionDiscountAllocator.Line> adjusted = lines.stream()
                .map(line -> new PromotionDiscountAllocator.Line(line.orderItemId(),
                        amount(line.baseAmount()).subtract(
                                specialByItem.getOrDefault(line.orderItemId(), BigDecimal.ZERO.setScale(SCALE)))))
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
        // 逐行基础金额回填**原始**行金额（不是特价后的），否则「原价多少」在分摊里就丢了
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
     * 主活动只记「实际产生优惠的第一条」：多组叠加时，快速展示能说出主规则是哪一条； 叠加生效的其余活动完整落在 {@code appliedActivities} 里，冻结时一并入快照。
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
     * 一条限时特价活动作用在匹配行上的让利，并把它记进 {@code specialByItem}。
     *
     * <p>
     * 让利 = 行基础金额 − 数量 × 特价；**特价 ≥ 该行单价时让利为 0** —— 特价只能把价格往下压， 不能抬高（否则协议客户的价格会被活动抬上去）。
     *
     * <p>
     * 同一行只让一次价：已被更靠前的一条特价命中过的行直接跳过。这样「按优先级顺序作用」 这条既有语义在特价上也成立，且每条活动记进快照的让利额之和恰好等于总让利。
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
        for (PromotionActivityEntity activity : candidates.stream().sorted(Comparator
                .comparing(PromotionActivityEntity::getPriority, Comparator.nullsFirst(Comparator.naturalOrder()))
                .reversed().thenComparing(PromotionActivityEntity::getId)).toList()) {
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
     * 门槛比的是**基础合计**（未扣任何优惠的订单金额），不是逐条作用后的剩余金额： 「满 100 赠 2kg」不该因为同单另有满减把门槛压没 —— 客户看的是订单金额达没达标。
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
     * 把满赠活动规则解析成一条赠品权益，并取赠品 SKU 快照。
     *
     * <p>
     * 赠品 SKU 读不到时**失败**而不是静默跳过：规则指向一个不存在的商品，冻结一条发不出货的权益 比当场报错更糟 —— 后者在确认订单时就能发现，前者要到发货才暴露。
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
