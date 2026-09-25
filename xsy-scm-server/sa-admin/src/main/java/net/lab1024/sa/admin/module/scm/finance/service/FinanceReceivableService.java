package net.lab1024.sa.admin.module.scm.finance.service;

import lombok.RequiredArgsConstructor;
import net.lab1024.sa.admin.module.scm.common.util.ScmDocumentNumbers;
import net.lab1024.sa.admin.module.scm.finance.constant.FinanceConstant;
import net.lab1024.sa.admin.module.scm.finance.constant.ScmFinanceBusinessTypeEnum;
import net.lab1024.sa.admin.module.scm.finance.constant.ScmFinanceEntryTypeEnum;
import net.lab1024.sa.admin.module.scm.finance.constant.ScmFinanceOperationTypeEnum;
import net.lab1024.sa.admin.module.scm.finance.constant.ScmFinanceReceivableItemSourceTypeEnum;
import net.lab1024.sa.admin.module.scm.finance.constant.ScmFinanceReceivableSourceTypeEnum;
import net.lab1024.sa.admin.module.scm.finance.dao.FinanceReceivableDao;
import net.lab1024.sa.admin.module.scm.finance.dao.FinanceReceivableItemDao;
import net.lab1024.sa.admin.module.scm.finance.dao.FinanceReceivableSourceDao;
import net.lab1024.sa.admin.module.scm.finance.domain.dto.FinanceReceivableSourceDto;
import net.lab1024.sa.admin.module.scm.finance.domain.dto.FinanceReceivableSourceLineDto;
import net.lab1024.sa.admin.module.scm.finance.domain.dto.FinanceReturnSourceDto;
import net.lab1024.sa.admin.module.scm.finance.domain.dto.FinanceReturnSourceLineDto;
import net.lab1024.sa.admin.module.scm.finance.domain.entity.FinanceReceivableEntity;
import net.lab1024.sa.admin.module.scm.finance.domain.entity.FinanceReceivableItemEntity;
import net.lab1024.sa.admin.module.scm.finance.support.FinanceOperationLogRecorder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 应收域服务：正常应收（签收派生）与红字应收（退货批准派生）。
 *
 * <p><b>红字没有任何金额上限校验</b>（第三批 D-2 / D-4，两条都裁为 A）：可生成额度不扣既有核销额，
 * 也不得超过原正常应收的余额——红字是「已经成立的 {@code OrderReturn APPROVED} 在财务域中的事实映射」，
 * 生成器抛错等于让财务规则反向控制订单域状态机。因此本类里<b>不存在</b>也<b>不得新增</b>
 * {@code FINANCE_RED_AMOUNT_EXCEEDED(41137)} 的使用：该码只服务 F1-4 的手工红字应付。
 * 允许净应收为负，其表达（{@code openAmount} / {@code overAppliedAmount}）属 F1-5 读侧派生。
 *
 * <p><b>边界</b>：对 {@code sales_order*} / {@code order_return*} / {@code inventory_*} /
 * {@code delivery_*} 只读（全局不变量 4，由 {@code FinanceReadOnlyContractTest} 静态扫描把守）；
 * 不实现历史回填（D-1，也即不提供任何补生成 API —— 补生成只是重复执行同一个派生生成器）；
 * 不产生收付款事实（退款付款属 F1-3，避免与红字双重冲减）。
 */
@Service
@RequiredArgsConstructor
public class FinanceReceivableService {

    private final FinanceReceivableDao receivables;
    private final FinanceReceivableItemDao receivableItems;
    private final FinanceReceivableSourceDao receivableSource;
    private final FinanceOperationLogRecorder operationLogs;

    /**
     * 签收 → 正常应收，并补生成该订单此前已批准退货的红字（第一批 Q1-Q4，第二批 Q27 三种时序）。
     *
     * <p>两步必须在这里连续做：{@code sign} 与 {@code approve} 是两条独立事务，
     * 只靠「退货批准时看一眼有没有正常应收」会漏账 —— 批准的那一方看不到尚未提交的签收，
     * 签收的一方也可能看不到刚刚提交的批准。共享串行点是订单行锁
     * （{@code OrderReturnService.lock} 与 {@code DeliveryRouteService.sign} 都先锁
     * {@code sales_order}），后拿到锁的一方在 {@code READ COMMITTED} 下一定能看见先提交的一方。
     *
     * <p><b>必须与签收同事务同成败</b>（{@code MANDATORY}）；跳过语义（零实发 / 整单 0 元）
     * 是成功返回，不回滚签收。
     *
     * @param deliveryRouteOrderId 刚被置为 {@code SIGNED} 的 {@code delivery_route_order.id}；
     *                             时点与操作人只存在于这一行，不能由调用方现取
     */
    @Transactional(propagation = Propagation.MANDATORY, rollbackFor = Exception.class)
    public void generateOnSign(Long deliveryRouteOrderId) {
        FinanceReceivableSourceDto source = receivableSource.selectSignedAssignment(deliveryRouteOrderId);
        if (source == null) {
            // 调用点把没签收（或不存在）的分配行当成签收事实。静默跳过会留下
            // 「配送以为已挂账、财务什么都没记」的缺口，因此必须失败并让签收回滚。
            throw new IllegalStateException(
                    "配送分配行未处于 SIGNED 状态，不能生成应收: " + deliveryRouteOrderId);
        }

        ensureNormalFromSigning(source);
        // 没有正常应收就没有可挂红字的原单，孤立红字被第二批 Q27 明令禁止 —— 因此不补生成。
        FinanceReceivableEntity normal = receivables.selectNormalByOrder(source.getSalesOrderId());
        if (normal != null) {
            backfillRedForApprovedReturns(source.getSalesOrderId(), normal);
        }
    }

    /**
     * 退货批准 → 红字应收（第二批 Q27、第三批 D-2 / D-4）。
     *
     * <p><b>正常应收尚不存在时成功跳过</b>：不创建孤立红字、不抛「原应收不存在」、不引入待处理状态，
     * 更不阻塞 {@code approve} —— 退货与退款单是订单域已经成立的事实。该订单后续签收时由
     * {@link #generateOnSign} 补生成（同一套实现，不复制第二份算法）。
     *
     * <p>重复执行（批准幂等重放、签收补生成、生成器重放）都收敛到「一张退货一张红字」：
     * 防重是 {@code uk_finance_receivable_source_active} 与 {@code ..._item_source_active}，
     * 本方法不吃 {@code Idempotency-Key}。
     *
     * @param orderReturnId 刚被置为 {@code APPROVED} 的 {@code order_return.id}
     */
    @Transactional(propagation = Propagation.MANDATORY, rollbackFor = Exception.class)
    public void generateRedOnReturnApproved(Long orderReturnId) {
        FinanceReturnSourceDto returned = receivableSource.selectApprovedReturn(orderReturnId);
        if (returned == null) {
            throw new IllegalStateException(
                    "退货单未处于 APPROVED 状态，不能生成红字应收: " + orderReturnId);
        }
        FinanceReceivableEntity normal = receivables.selectNormalByOrder(returned.getOrderId());
        if (normal == null) {
            // 退货先于签收（第二批 Q27 的第二种时序）：成功跳过，等签收补生成。
            return;
        }
        generateRed(returned, normal);
    }

    /**
     * 确保该订单的正常应收存在：本次插入，或已被并发的另一次签收插入（那一次才是事实的产生者）。
     *
     * <p>零实发与整单金额为 0 时什么都不写、静默返回 —— 跳过是成功语义，不是失败。
     */
    private void ensureNormalFromSigning(FinanceReceivableSourceDto source) {
        List<FinanceReceivableItemEntity> items = toNormalItems(source,
                receivableSource.selectOutboundLines(source.getSalesOrderId()));
        BigDecimal amount = items.stream()
                .map(FinanceReceivableItemEntity::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (amount.signum() <= 0) {
            // 两种形状都在这里跳过：① 签收成功但零实发（第二批 Q8）；② 售价全为 0。
            // 跳过是成功语义：绝不为此回滚合法签收，也绝不造空单头或 0 元事实留痕。
            return;
        }

        FinanceReceivableEntity receivable = normalHeader(source, amount);
        if (receivables.insertOnConflictDoNothing(receivable) == 0) {
            // 该订单已经有正常应收了（生成器可重放：签收重试、同一派生被再次触发）。
            // 首笔已经把明细与日志写全，这里既不再插也不重复留痕 —— 仲裁者是那条唯一索引，
            // 不是「先查再插」。
            return;
        }
        for (FinanceReceivableItemEntity item : items) {
            item.setReceivableId(receivable.getId());
            if (receivableItems.insertOnConflictDoNothing(item) != 1) {
                // 单头是本次刚插入的，因此这里撞键不是重放，而是同一出库行被挂到了两张应收单上。
                throw new IllegalStateException(
                        "出库行已挂在别的应收单上，本次生成整体回滚: " + item.getSourceId());
            }
        }

        operationLogs.record(ScmFinanceBusinessTypeEnum.RECEIVABLE, receivable.getId(),
                ScmFinanceOperationTypeEnum.GENERATE, null, null,
                generatedSnapshot(receivable, items.size()), source.getSignedBy());
    }

    /**
     * 补生成该订单全部已批准退货的红字（第二批 Q27 的第三种时序）。
     *
     * <p>逐张走与「批准时直接触发」完全相同的那一个 {@link #generateRed} 实现：
     * 只有一份红字算法，两条触发路径的差别只在「什么时候被叫到」。
     */
    private void backfillRedForApprovedReturns(Long salesOrderId, FinanceReceivableEntity normal) {
        for (Long returnId : receivableSource.selectApprovedReturnIds(salesOrderId)) {
            FinanceReturnSourceDto returned = receivableSource.selectApprovedReturn(returnId);
            if (returned == null) {
                // 同一事务内刚按 APPROVED 查出这一张，现在读不到说明数据被旁路改过：宁可失败不可漏账。
                throw new IllegalStateException("已批准退货读不到事实，不能补生成红字应收: " + returnId);
            }
            generateRed(returned, normal);
        }
    }

    /**
     * 红字生成算法本体（唯一实现）。
     *
     * <p>结算对方与名称快照一律继承原正常应收：红字与正常必须落在同一个客户账上，
     * 从订单或退货行重新解析快照会让同一笔债权出现两个对方身份
     * （第三批 Q27「必须引用原 {@code Receivable}」的含意之一）。
     */
    private void generateRed(FinanceReturnSourceDto returned, FinanceReceivableEntity normal) {
        List<FinanceReceivableItemEntity> items = toRedItems(returned,
                receivableSource.selectApprovedReturnLines(returned.getOrderReturnId()));
        BigDecimal amount = items.stream()
                .map(FinanceReceivableItemEntity::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (amount.signum() <= 0) {
            // 整张退货没有有效红字行（全部非正金额行）：成功跳过，
            // 不生成红字单头 / 明细 / 日志，也不影响已经合法成立的 Return APPROVED。
            return;
        }

        FinanceReceivableEntity red = redHeader(returned, normal, amount);
        if (receivables.insertOnConflictDoNothing(red) == 0) {
            return;
        }
        for (FinanceReceivableItemEntity item : items) {
            item.setReceivableId(red.getId());
            if (receivableItems.insertOnConflictDoNothing(item) != 1) {
                throw new IllegalStateException(
                        "退货行已挂在别的应收单上，本次红字生成整体回滚: " + item.getSourceId());
            }
        }

        operationLogs.record(ScmFinanceBusinessTypeEnum.RECEIVABLE, red.getId(),
                ScmFinanceOperationTypeEnum.RED_GENERATE, returned.getReason(), null,
                redGeneratedSnapshot(red, returned, items.size()), returned.getApprovedBy());
    }

    /**
     * 正常应收单头。{@code originalReceivableId} 与 {@code reason} 保持 {@code null}
     * （{@code ck_finance_receivable_entry_pairing} 对 NORMAL 的要求）。
     */
    private FinanceReceivableEntity normalHeader(FinanceReceivableSourceDto source, BigDecimal amount) {
        OffsetDateTime now = OffsetDateTime.now();

        FinanceReceivableEntity receivable = new FinanceReceivableEntity();
        receivable.setReceivableNo(ScmDocumentNumbers.format(
                FinanceConstant.RECEIVABLE_NO_PREFIX, receivables.nextReceivableNo()));
        receivable.setSourceType(ScmFinanceReceivableSourceTypeEnum.SALES_ORDER.name());
        receivable.setSourceId(source.getSalesOrderId());
        receivable.setOrderId(source.getSalesOrderId());
        receivable.setCustomerId(source.getCustomerId());
        receivable.setCustomerNameSnapshot(source.getCustomerNameSnapshot());
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
     * 红字应收单头。事件时点与原因继承退货业务事实，金额取已批准红字行之和；
     * **不做任何上限比较**（D-2 / D-4）。
     */
    private FinanceReceivableEntity redHeader(FinanceReturnSourceDto returned,
                                              FinanceReceivableEntity normal, BigDecimal amount) {
        OffsetDateTime now = OffsetDateTime.now();

        FinanceReceivableEntity red = new FinanceReceivableEntity();
        red.setReceivableNo(ScmDocumentNumbers.format(
                FinanceConstant.RECEIVABLE_NO_PREFIX, receivables.nextReceivableNo()));
        red.setSourceType(ScmFinanceReceivableSourceTypeEnum.ORDER_RETURN.name());
        red.setSourceId(returned.getOrderReturnId());
        red.setOrderId(returned.getOrderId());
        red.setCustomerId(normal.getCustomerId());
        red.setCustomerNameSnapshot(normal.getCustomerNameSnapshot());
        red.setEntryType(ScmFinanceEntryTypeEnum.RED.name());
        red.setOriginalReceivableId(normal.getId());
        red.setAmount(amount);
        red.setEventAt(returned.getApprovedAt());
        // 红字原因必须非空（ck_finance_receivable_entry_pairing）；order_return.reason
        // 由 ck_order_return_reason 在库级保证非空白，因此不需要财务侧另造一句原因。
        red.setReason(returned.getReason());
        red.setCreatedAt(now);
        red.setUpdatedAt(now);
        red.setCreatedBy(returned.getApprovedBy());
        red.setUpdatedBy(returned.getApprovedBy());
        return red;
    }

    /**
     * 正常明细：量取出库行、价取订单行的冻结售价，金额 {@code ROUND(量 × 价, 4, HALF_UP)}
     * （第二批 Q22）；单头是**已舍入行金额之和**。一条订单行对应多条出库行时逐条成行、不合并。
     */
    private List<FinanceReceivableItemEntity> toNormalItems(FinanceReceivableSourceDto source,
                                                            List<FinanceReceivableSourceLineDto> lines) {
        OffsetDateTime now = OffsetDateTime.now();

        return lines.stream().map(line -> {
            BigDecimal amount = line.getQuantity().multiply(line.getUnitPrice())
                    .setScale(FinanceConstant.AMOUNT_SCALE, RoundingMode.HALF_UP);

            FinanceReceivableItemEntity item = newItem(source.getSignedBy(), now);
            item.setSourceType(ScmFinanceReceivableItemSourceTypeEnum.INVENTORY_OUTBOUND_ITEM.name());
            item.setSourceId(line.getInventoryOutboundItemId());
            item.setOrderItemId(line.getSalesOrderItemId());
            item.setSkuId(line.getSkuId());
            item.setSkuNameSnapshot(line.getSkuNameSnapshot());
            item.setUnitSnapshot(line.getUnitSnapshot());
            item.setQuantity(line.getQuantity());
            item.setUnitPrice(line.getUnitPrice());
            item.setAmount(amount);
            return item;
        }).toList();
    }

    /**
     * 红字明细：金额直接采用订单域已落库的 {@code approved_amount}，
     * **不重算** {@code quantity × unit_price}（设计稿 §8.2）；也不存行级原明细指针
     * ——一条订单行可能对应多条出库行，不存在唯一的原正常明细（§3.2）。
     */
    private List<FinanceReceivableItemEntity> toRedItems(FinanceReturnSourceDto returned,
                                                         List<FinanceReturnSourceLineDto> lines) {
        OffsetDateTime now = OffsetDateTime.now();

        return lines.stream().map(line -> {
            FinanceReceivableItemEntity item = newItem(returned.getApprovedBy(), now);
            item.setSourceType(ScmFinanceReceivableItemSourceTypeEnum.ORDER_RETURN_ITEM.name());
            item.setSourceId(line.getOrderReturnItemId());
            item.setOrderItemId(line.getOrderItemId());
            item.setSkuId(line.getSkuId());
            item.setSkuNameSnapshot(line.getSkuNameSnapshot());
            item.setUnitSnapshot(line.getUnitSnapshot());
            item.setQuantity(line.getApprovedQuantity());
            item.setUnitPrice(line.getLockedUnitPrice());
            item.setAmount(line.getApprovedAmount()
                    .setScale(FinanceConstant.AMOUNT_SCALE, RoundingMode.HALF_UP));
            return item;
        }).toList();
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
     * 生成类动作的 {@code after_data} 单头快照（§17）。金额与时间落成字符串：JSONB 侧的
     * {@code JsonbObjectMapTypeHandler} 用的是未注册 JavaTimeModule 的裸 ObjectMapper。
     */
    private Map<String, Object> generatedSnapshot(FinanceReceivableEntity receivable, int itemCount) {
        Map<String, Object> snapshot = baseSnapshot(receivable);
        snapshot.put("itemCount", itemCount);
        return snapshot;
    }

    /**
     * 红字的 {@code after_data}：除单头快照外必须能直接看出它冲的是哪张原应收、来源是哪张退货单
     * （§18），否则事后核对要连表跳三次。
     */
    private Map<String, Object> redGeneratedSnapshot(FinanceReceivableEntity red,
                                                     FinanceReturnSourceDto returned, int itemCount) {
        Map<String, Object> snapshot = baseSnapshot(red);
        snapshot.put("sourceReturnId", returned.getOrderReturnId());
        snapshot.put("returnNo", returned.getReturnNo());
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
