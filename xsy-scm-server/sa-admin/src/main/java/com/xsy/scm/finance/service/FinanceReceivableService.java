package com.xsy.scm.finance.service;

import com.xsy.scm.common.util.ScmDocumentNumbers;
import com.xsy.scm.finance.constant.FinanceConstant;
import com.xsy.scm.finance.constant.ScmFinanceBusinessTypeEnum;
import com.xsy.scm.finance.constant.ScmFinanceEntryTypeEnum;
import com.xsy.scm.finance.constant.ScmFinanceOperationTypeEnum;
import com.xsy.scm.finance.constant.ScmFinanceReceivableItemSourceTypeEnum;
import com.xsy.scm.finance.constant.ScmFinanceReceivableSourceTypeEnum;
import com.xsy.scm.finance.dao.FinanceReceivableDao;
import com.xsy.scm.finance.dao.FinanceReceivableItemDao;
import com.xsy.scm.finance.dao.FinanceReceivableSourceDao;
import com.xsy.scm.finance.domain.dto.FinanceIndependentReturnSourceDto;
import com.xsy.scm.finance.domain.dto.FinanceOrderDiscountLineDto;
import com.xsy.scm.finance.domain.dto.FinanceReceivableSourceDto;
import com.xsy.scm.finance.domain.dto.FinanceReceivableSourceLineDto;
import com.xsy.scm.finance.domain.dto.FinanceReturnSourceDto;
import com.xsy.scm.finance.domain.dto.FinanceReturnSourceLineDto;
import com.xsy.scm.finance.domain.entity.FinanceReceivableEntity;
import com.xsy.scm.finance.domain.entity.FinanceReceivableItemEntity;
import com.xsy.scm.finance.support.FinanceOperationLogRecorder;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 根据签收生成应收，根据批准退货生成红字应收。红字金额按已批准退货计算，不受当前未收余额限制，也不能阻止退货审批。 本服务只写财务应收及操作日志，不直接修改订单和库存。
 */
@Service
@RequiredArgsConstructor
public class FinanceReceivableService {

    private final FinanceReceivableDao financeReceivableDao;
    private final FinanceReceivableItemDao financeReceivableItemDao;
    private final FinanceReceivableSourceDao financeReceivableSourceDao;
    private final FinanceOperationLogRecorder operationLogs;
    private final FinanceOrderFundingSettlementService financeOrderFundingSettlementService;

    /**
     * 签收后生成正常应收，并补齐之前已批准退货的红字应收。签收和退货审批通过订单行锁串行处理，避免并发漏账。生成过程与签收处于同一事务。
     *
     * @param deliveryRouteOrderId
     *            已签收的配送线路订单 ID。
     */
    @Transactional(propagation = Propagation.MANDATORY, rollbackFor = Exception.class)
    public void generateOnSign(Long deliveryRouteOrderId) {
        FinanceReceivableSourceDto source = financeReceivableSourceDao.selectSignedAssignment(deliveryRouteOrderId);
        if (source == null) {
            // 调用点把没签收（或不存在）的分配行当成已签收。跳过会留下
            // 「配送以为已挂账、财务什么都没记」的缺口，因此必须失败并让签收回滚。
            throw new IllegalStateException("配送分配行未处于 SIGNED 状态，不能生成应收: " + deliveryRouteOrderId);
        }

        ensureNormalFromSigning(source);
        // 没有正常应收就没有可挂红字的原单，因此不生成孤立红字。
        FinanceReceivableEntity normal = financeReceivableDao.selectNormalByOrder(source.getSalesOrderId());
        if (normal != null) {
            backfillRedForApprovedReturns(source.getSalesOrderId(), normal);
            financeOrderFundingSettlementService.settleSalesOrderFunding(source.getSalesOrderId());
        }
    }

    /**
     * 退货批准后生成红字应收。原应收尚未生成时先跳过，后续签收会补齐；重复执行不会重复记账。
     *
     * @param orderReturnId
     *            已批准的退货单 ID
     * @return 按保存的优惠分摊计算的退货净额
     */
    @Transactional(propagation = Propagation.MANDATORY, rollbackFor = Exception.class)
    public BigDecimal generateRedOnReturnApproved(Long orderReturnId) {
        FinanceReturnSourceDto returned = financeReceivableSourceDao.selectApprovedReturn(orderReturnId);
        if (returned == null) {
            throw new IllegalStateException("退货单未处于 APPROVED 状态，不能生成红字应收: " + orderReturnId);
        }
        FinanceReceivableEntity normal = financeReceivableDao.selectNormalByOrder(returned.getOrderId());
        // 同一个红字算法既计算退款净额，也在正常应收已存在时生成红字；
        // 退货先于签收时只返回净额，等签收时仍由同一算法补生成红字。
        return generateRed(returned, normal);
    }

    /**
     * 独立退货批准后生成客户级红字应收：挂在客户名下，不挂原订单。
     *
     * <p>
     * 没有原订单，也没有可指定的原应收：红字只挂客户与结算主体，由账龄、核销按客户先进先出抵减最早未结应收。 金额取审批时保存的行金额之和，不重新取价；没有有效金额行时成功跳过。
     *
     * @param orderReturnId
     *            已批准的独立退货单 ID
     * @return 红字金额
     */
    @Transactional(propagation = Propagation.MANDATORY, rollbackFor = Exception.class)
    public BigDecimal generateRedOnIndependentReturnApproved(Long orderReturnId) {
        FinanceIndependentReturnSourceDto returned = financeReceivableSourceDao
            .selectApprovedIndependentReturn(orderReturnId);
        if (returned == null) {
            throw new IllegalStateException("独立退货单未处于 APPROVED 状态，不能生成客户级红字: " + orderReturnId);
        }
        // 独立退货没有订单优惠可反向，优惠分摊固定为 0；这里必须用允许 null 键的映射，行的 orderItemId 为空。
        List<FinanceReceivableItemEntity> items = toRedItems(returned.getApprovedBy(),
            financeReceivableSourceDao.selectApprovedIndependentReturnLines(orderReturnId), new HashMap<>());
        BigDecimal amount = items.stream().map(FinanceReceivableItemEntity::getAmount).reduce(BigDecimal.ZERO,
            BigDecimal::add);
        if (amount.signum() <= 0) {
            return amount;
        }
        FinanceReceivableEntity red = independentRedHeader(returned, amount);
        if (financeReceivableDao.insertOnConflictDoNothing(red) == 0) {
            return amount;
        }
        for (FinanceReceivableItemEntity item : items) {
            item.setReceivableId(red.getId());
            if (financeReceivableItemDao.insertOnConflictDoNothing(item) != 1) {
                throw new IllegalStateException("退货行已挂在别的应收单上，本次红字生成整体回滚: " + item.getSourceId());
            }
        }
        operationLogs.record(ScmFinanceBusinessTypeEnum.RECEIVABLE, red.getId(),
            ScmFinanceOperationTypeEnum.RED_GENERATE, returned.getReason(), null,
            redGeneratedSnapshot(red, returned.getReturnNo(), returned.getOrderReturnId(), items.size()),
            returned.getApprovedBy());
        return amount;
    }

    /**
     * 确保该订单的正常应收存在：本次插入，或已被并发的另一次签收插入（那一次才是这条记录的创建者）。
     *
     * <p>
     * 零实发与整单金额为 0 时什么都不写、直接返回——跳过是正常成功，不是失败。
     */
    private void ensureNormalFromSigning(FinanceReceivableSourceDto source) {
        if (financeReceivableSourceDao.countIncompleteBundleOutbound(source.getSalesOrderId()) > 0) {
            throw new IllegalStateException("组合商品出库组成不完整，不能生成应收: " + source.getSalesOrderId());
        }
        List<FinanceReceivableItemEntity> items = toNormalItems(source,
            financeReceivableSourceDao.selectOutboundLines(source.getSalesOrderId()),
            orderLineDiscounts(source.getSalesOrderId()));
        BigDecimal amount = items.stream().map(FinanceReceivableItemEntity::getAmount).reduce(BigDecimal.ZERO,
            BigDecimal::add);

        if (amount.signum() <= 0) {
            // 两种形状都在这里跳过：① 签收成功但零实发；② 售价全为 0。
            // 跳过是正常结果：绝不为此回滚合法签收，也绝不建空单头或留 0 元记录。
            return;
        }

        FinanceReceivableEntity receivable = normalHeader(source, amount);
        if (financeReceivableDao.insertOnConflictDoNothing(receivable) == 0) {
            // 该订单已经有正常应收了（生成器可重放：签收重试、同一派生被再次触发）。
            // 首笔已经把明细与日志写全，这里不再插入也不重复记录——由那条唯一索引仲裁，不靠「先查再插」。
            return;
        }
        for (FinanceReceivableItemEntity item : items) {
            item.setReceivableId(receivable.getId());
            if (financeReceivableItemDao.insertOnConflictDoNothing(item) != 1) {
                // 单头是本次刚插入的，因此这里撞键不是重放，而是同一出库行被挂到了两张应收单上。
                throw new IllegalStateException("出库行已挂在别的应收单上，本次生成整体回滚: " + item.getSourceId());
            }
        }

        operationLogs.record(ScmFinanceBusinessTypeEnum.RECEIVABLE, receivable.getId(),
            ScmFinanceOperationTypeEnum.GENERATE, null, null, generatedSnapshot(receivable, items.size()),
            source.getSignedBy());
    }

    /**
     * 补生成该订单全部已批准退货的红字（的第三种时序）。
     *
     * <p>
     * 逐张走与「批准时直接触发」完全相同的那一个 {@link #generateRed} 实现：只有一份红字算法，两条触发路径的差别只在「什么时候被叫到」。
     */
    private void backfillRedForApprovedReturns(Long salesOrderId, FinanceReceivableEntity normal) {
        for (Long returnId : financeReceivableSourceDao.selectApprovedReturnIds(salesOrderId)) {
            FinanceReturnSourceDto returned = financeReceivableSourceDao.selectApprovedReturn(returnId);
            if (returned == null) {
                // 同一事务内刚按 APPROVED 查出这一张，现在读不到说明数据被其他途径改过：宁可失败不可漏账。
                throw new IllegalStateException("已批准退货读不到记录，不能补生成红字应收: " + returnId);
            }
            generateRed(returned, normal);
        }
    }

    /**
     * 红字金额计算与生成（唯一实现）。
     *
     * <p>
     * 结算对方与名称一律继承原正常应收：红字与正常必须落在同一个客户账上，从订单或退货行重新取值会让同一笔债权出现两个对方身份。 正常应收尚未生成时，本方法仍返回相同净额，签收后再补生成红字。
     */
    private BigDecimal generateRed(FinanceReturnSourceDto returned, FinanceReceivableEntity normal) {
        List<FinanceReceivableItemEntity> items = toRedItems(returned.getApprovedBy(),
            financeReceivableSourceDao.selectApprovedReturnLines(returned.getOrderReturnId()),
            orderLineDiscounts(returned.getOrderId()));
        BigDecimal amount = items.stream().map(FinanceReceivableItemEntity::getAmount).reduce(BigDecimal.ZERO,
            BigDecimal::add);

        if (amount.signum() <= 0 || normal == null) {
            // 整张退货没有有效红字行（全部非正金额行）：成功跳过，
            // 或正常应收尚未生成；两种情况都不生成红字，但退款仍按保存的优惠分摊计算。
            return amount;
        }

        FinanceReceivableEntity red = redHeader(returned, normal, amount);
        if (financeReceivableDao.insertOnConflictDoNothing(red) == 0) {
            return amount;
        }
        for (FinanceReceivableItemEntity item : items) {
            item.setReceivableId(red.getId());
            if (financeReceivableItemDao.insertOnConflictDoNothing(item) != 1) {
                throw new IllegalStateException("退货行已挂在别的应收单上，本次红字生成整体回滚: " + item.getSourceId());
            }
        }

        operationLogs.record(ScmFinanceBusinessTypeEnum.RECEIVABLE, red.getId(),
            ScmFinanceOperationTypeEnum.RED_GENERATE, returned.getReason(), null,
            redGeneratedSnapshot(red, returned, items.size()), returned.getApprovedBy());
        return amount;
    }

    /**
     * 正常应收单头。{@code originalReceivableId} 与 {@code reason} 保持 {@code null} （{@code ck_finance_receivable_entry_pairing}
     * 对 NORMAL 的要求）。
     */
    private FinanceReceivableEntity normalHeader(FinanceReceivableSourceDto source, BigDecimal amount) {
        OffsetDateTime now = OffsetDateTime.now();

        FinanceReceivableEntity receivable = new FinanceReceivableEntity();
        receivable.setReceivableNo(
            ScmDocumentNumbers.format(FinanceConstant.RECEIVABLE_NO_PREFIX, financeReceivableDao.nextReceivableNo()));
        receivable.setSourceType(ScmFinanceReceivableSourceTypeEnum.SALES_ORDER.name());
        receivable.setSourceId(source.getSalesOrderId());
        receivable.setOrderId(source.getSalesOrderId());
        receivable.setCustomerId(source.getCustomerId());
        receivable.setCustomerNameSnapshot(source.getCustomerNameSnapshot());
        receivable.setSettlementCustomerId(source.getSettlementCustomerId());
        receivable.setSettlementCustomerNameSnapshot(source.getSettlementCustomerNameSnapshot());
        receivable.setEntryType(ScmFinanceEntryTypeEnum.NORMAL.name());
        receivable.setAmount(amount);
        receivable.setEventAt(source.getSignedAt());
        receivable.setCreatedAt(now);
        receivable.setUpdatedAt(now);
        receivable.setCreatedBy(source.getSignedBy());
        receivable.setUpdatedBy(source.getSignedBy());
        return receivable;
    }

    /**
     * 红字应收单头。事件时间与原因取自退货记录，金额取已批准红字行之和；不做任何上限比较。
     */
    private FinanceReceivableEntity redHeader(FinanceReturnSourceDto returned, FinanceReceivableEntity normal,
        BigDecimal amount) {
        OffsetDateTime now = OffsetDateTime.now();

        FinanceReceivableEntity red = new FinanceReceivableEntity();
        red.setReceivableNo(
            ScmDocumentNumbers.format(FinanceConstant.RECEIVABLE_NO_PREFIX, financeReceivableDao.nextReceivableNo()));
        red.setSourceType(ScmFinanceReceivableSourceTypeEnum.ORDER_RETURN.name());
        red.setSourceId(returned.getOrderReturnId());
        red.setOrderId(returned.getOrderId());
        red.setCustomerId(normal.getCustomerId());
        red.setCustomerNameSnapshot(normal.getCustomerNameSnapshot());
        red.setSettlementCustomerId(normal.getSettlementCustomerId());
        red.setSettlementCustomerNameSnapshot(normal.getSettlementCustomerNameSnapshot());
        red.setDueDate(normal.getDueDate());
        red.setCreditRuleSnapshot(normal.getCreditRuleSnapshot());
        red.setEntryType(ScmFinanceEntryTypeEnum.RED.name());
        red.setOriginalReceivableId(normal.getId());
        red.setAmount(amount);
        red.setEventAt(returned.getApprovedAt());
        // 红字原因必须非空（ck_finance_receivable_entry_pairing）；order_return.reason
        // 已由 ck_order_return_reason 在数据库层保证非空白，因此不需要财务侧另造一句原因。
        red.setReason(returned.getReason());
        red.setCreatedAt(now);
        red.setUpdatedAt(now);
        red.setCreatedBy(returned.getApprovedBy());
        red.setUpdatedBy(returned.getApprovedBy());
        return red;
    }

    /**
     * 客户级红字单头：来源是独立退货单，订单与原应收都为空；到期日与信用规则由数据库触发器按 RED 分支留空。
     */
    private FinanceReceivableEntity independentRedHeader(FinanceIndependentReturnSourceDto returned,
        BigDecimal amount) {
        OffsetDateTime now = OffsetDateTime.now();

        FinanceReceivableEntity red = new FinanceReceivableEntity();
        red.setReceivableNo(
            ScmDocumentNumbers.format(FinanceConstant.RECEIVABLE_NO_PREFIX, financeReceivableDao.nextReceivableNo()));
        red.setSourceType(ScmFinanceReceivableSourceTypeEnum.ORDER_RETURN.name());
        red.setSourceId(returned.getOrderReturnId());
        red.setOrderId(null);
        red.setCustomerId(returned.getCustomerId());
        red.setCustomerNameSnapshot(returned.getCustomerNameSnapshot());
        red.setSettlementCustomerId(returned.getSettlementCustomerId());
        red.setSettlementCustomerNameSnapshot(returned.getSettlementCustomerNameSnapshot());
        red.setEntryType(ScmFinanceEntryTypeEnum.RED.name());
        red.setOriginalReceivableId(null);
        red.setAmount(amount);
        red.setEventAt(returned.getApprovedAt());
        red.setReason(returned.getReason());
        red.setCreatedAt(now);
        red.setUpdatedAt(now);
        red.setCreatedBy(returned.getApprovedBy());
        red.setUpdatedBy(returned.getApprovedBy());
        return red;
    }

    /**
     * 正常明细：数量取出库行、价格取订单行保存的售价，毛额 {@code ROUND(量 × 价, 4, HALF_UP)}，再扣该订单行承担的优惠分摊得到净额； 单头是已舍入行净额之和。一条订单行对应多条出库行时逐条成行、不合并。
     */
    private List<FinanceReceivableItemEntity> toNormalItems(FinanceReceivableSourceDto source,
        List<FinanceReceivableSourceLineDto> lines, Map<Long, BigDecimal> orderLineDiscounts) {
        OffsetDateTime now = OffsetDateTime.now();

        return lines.stream().map(line -> {
            BigDecimal gross = line.getQuantity().multiply(line.getUnitPrice()).setScale(FinanceConstant.AMOUNT_SCALE,
                RoundingMode.HALF_UP);
            BigDecimal discount = discountShare(line.getSalesOrderItemId(), gross, line.getOrderedLineAmount(),
                orderLineDiscounts);

            FinanceReceivableItemEntity item = newItem(source.getSignedBy(), now);
            item.setSourceType(ScmFinanceReceivableItemSourceTypeEnum.INVENTORY_OUTBOUND_ITEM.name());
            item.setSourceId(line.getInventoryOutboundItemId());
            item.setOrderItemId(line.getSalesOrderItemId());
            item.setSkuId(line.getSkuId());
            item.setSkuNameSnapshot(line.getSkuNameSnapshot());
            item.setUnitSnapshot(line.getUnitSnapshot());
            item.setQuantity(line.getQuantity());
            item.setUnitPrice(line.getUnitPrice());
            item.setDiscountAmount(discount);
            item.setAmount(gross.subtract(discount));
            return item;
        }).toList();
    }

    /**
     * 红字明细：毛额直接采用订单域已保存的 {@code approved_amount}，不重算 {@code quantity × unit_price}；
     * 再扣按同一算法算出的反向优惠得到净额。也不存行级原明细指针——一条订单行可能对应多条出库行，不存在唯一的原正常明细。
     */
    private List<FinanceReceivableItemEntity> toRedItems(String approvedBy, List<FinanceReturnSourceLineDto> lines,
        Map<Long, BigDecimal> orderLineDiscounts) {
        OffsetDateTime now = OffsetDateTime.now();

        return lines.stream().map(line -> {
            BigDecimal gross = line.getApprovedAmount().setScale(FinanceConstant.AMOUNT_SCALE, RoundingMode.HALF_UP);
            BigDecimal discount = discountShare(line.getOrderItemId(), gross, line.getOrderedLineAmount(),
                orderLineDiscounts);

            FinanceReceivableItemEntity item = newItem(approvedBy, now);
            item.setSourceType(ScmFinanceReceivableItemSourceTypeEnum.ORDER_RETURN_ITEM.name());
            item.setSourceId(line.getOrderReturnItemId());
            item.setOrderItemId(line.getOrderItemId());
            item.setSkuId(line.getSkuId());
            item.setSkuNameSnapshot(line.getSkuNameSnapshot());
            item.setUnitSnapshot(line.getUnitSnapshot());
            item.setQuantity(line.getApprovedQuantity());
            item.setUnitPrice(line.getLockedUnitPrice());
            item.setDiscountAmount(discount);
            item.setAmount(gross.subtract(discount));
            return item;
        }).toList();
    }

    /**
     * 订单确认时保存的行级优惠分摊：{@code orderItemId -> discountAmount}；订单无优惠时为空表。
     */
    private Map<Long, BigDecimal> orderLineDiscounts(Long salesOrderId) {
        Map<Long, BigDecimal> discounts = new HashMap<>();
        for (FinanceOrderDiscountLineDto line : financeReceivableSourceDao.selectOrderDiscountLines(salesOrderId)) {
            if (line.getOrderItemId() != null && line.getDiscountAmount() != null) {
                discounts.put(line.getOrderItemId(), line.getDiscountAmount());
            }
        }
        return discounts;
    }

    /**
     * 行级优惠分摊：把保存的订单行优惠按「该行本次金额 / 该订单行下单金额」等比折算到本次行金额上。
     *
     * <p>
     * 必须等比：优惠是按下单金额保存的，少发或部分退货时该行应承担的优惠要按比例变小。整额落到部分金额上会减出负数（被数据库 CHECK 拒绝）， 也会让部分退货多冲优惠。
     *
     * <p>
     * 正常与红字共用本方法，因此「已确认优惠 × 退货金额 / 已出库金额」与「保存的行分摊 × 退货金额 / 下单金额」恒等， 红字不必回读正常应收明细就能得到一致的反向额。
     *
     * <p>
     * 下单金额缺失或为 0（历史数据 / 0 元单）时按 0 处理，而不是把整额优惠压到这一行：没有可依的比例时「不减」比「乱减」更可解释。
     */
    static BigDecimal discountShare(Long orderItemId, BigDecimal lineAmount, BigDecimal orderedLineAmount,
        Map<Long, BigDecimal> orderLineDiscounts) {
        BigDecimal allocated = orderLineDiscounts.get(orderItemId);
        if (allocated == null || allocated.signum() <= 0 || lineAmount.signum() <= 0 || orderedLineAmount == null
            || orderedLineAmount.signum() <= 0) {
            return BigDecimal.ZERO.setScale(FinanceConstant.AMOUNT_SCALE);
        }
        BigDecimal share = lineAmount.multiply(allocated).divide(orderedLineAmount, FinanceConstant.AMOUNT_SCALE,
            RoundingMode.HALF_UP);
        return share.min(lineAmount).max(BigDecimal.ZERO);
    }

    private FinanceReceivableItemEntity newItem(String operator, OffsetDateTime now) {
        FinanceReceivableItemEntity item = new FinanceReceivableItemEntity();
        item.setCreatedAt(now);
        item.setUpdatedAt(now);
        item.setCreatedBy(operator);
        item.setUpdatedBy(operator);
        return item;
    }

    /**
     * 生成类动作的 {@code after_data} 单头快照。金额与时间写成字符串：JSONB 侧的 {@code JsonbObjectMapTypeHandler} 用的是未注册 JavaTimeModule 的
     * ObjectMapper。
     */
    private Map<String, Object> generatedSnapshot(FinanceReceivableEntity receivable, int itemCount) {
        Map<String, Object> snapshot = baseSnapshot(receivable);
        snapshot.put("itemCount", itemCount);
        return snapshot;
    }

    /**
     * 红字的 {@code after_data}：除单头快照外必须能直接看出它冲的是哪张原应收、来源是哪张退货单 ，否则事后核对要连表跳三次。
     */
    private Map<String, Object> redGeneratedSnapshot(FinanceReceivableEntity red, FinanceReturnSourceDto returned,
        int itemCount) {
        return redGeneratedSnapshot(red, returned.getReturnNo(), returned.getOrderReturnId(), itemCount);
    }

    private Map<String, Object> redGeneratedSnapshot(FinanceReceivableEntity red, String returnNo, Long returnId,
        int itemCount) {
        Map<String, Object> snapshot = baseSnapshot(red);
        snapshot.put("sourceReturnId", returnId);
        snapshot.put("returnNo", returnNo);
        snapshot.put("originalReceivableId", red.getOriginalReceivableId());
        snapshot.put("itemCount", itemCount);
        return snapshot;
    }

    private Map<String, Object> baseSnapshot(FinanceReceivableEntity receivable) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("receivableNo", receivable.getReceivableNo());
        snapshot.put("sourceType", receivable.getSourceType());
        snapshot.put("sourceId", receivable.getSourceId());
        snapshot.put("orderId", receivable.getOrderId());
        snapshot.put("customerId", receivable.getCustomerId());
        snapshot.put("customerNameSnapshot", receivable.getCustomerNameSnapshot());
        snapshot.put("entryType", receivable.getEntryType());
        snapshot.put("amount", receivable.getAmount().toPlainString());
        snapshot.put("eventAt", receivable.getEventAt().toString());
        return snapshot;
    }
}
