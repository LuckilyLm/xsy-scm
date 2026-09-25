package net.lab1024.sa.admin.module.scm.finance.service;

import lombok.RequiredArgsConstructor;
import net.lab1024.sa.admin.module.scm.common.constant.ScmOperator;
import net.lab1024.sa.admin.module.scm.common.util.ScmDocumentNumbers;
import net.lab1024.sa.admin.module.scm.finance.constant.FinanceConstant;
import net.lab1024.sa.admin.module.scm.finance.constant.ScmFinanceBusinessTypeEnum;
import net.lab1024.sa.admin.module.scm.finance.constant.ScmFinanceEntryTypeEnum;
import net.lab1024.sa.admin.module.scm.finance.constant.ScmFinanceOperationTypeEnum;
import net.lab1024.sa.admin.module.scm.finance.constant.ScmFinancePayableItemSourceTypeEnum;
import net.lab1024.sa.admin.module.scm.finance.constant.ScmFinancePayableSourceTypeEnum;
import net.lab1024.sa.admin.module.scm.finance.dao.FinancePayableDao;
import net.lab1024.sa.admin.module.scm.finance.dao.FinancePayableItemDao;
import net.lab1024.sa.admin.module.scm.finance.dao.FinancePayableSourceDao;
import net.lab1024.sa.admin.module.scm.finance.domain.dto.FinancePayableSourceDto;
import net.lab1024.sa.admin.module.scm.finance.domain.dto.FinancePayableSourceLineDto;
import net.lab1024.sa.admin.module.scm.finance.domain.entity.FinancePayableEntity;
import net.lab1024.sa.admin.module.scm.finance.domain.entity.FinancePayableItemEntity;
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
 * 应付域服务。
 *
 * <p><b>F1-4 在此实现</b>：手工红字应付登记（{@code scm:finance:payable:red}）。
 * 这是 {@code FINANCE_RED_AMOUNT_EXCEEDED(41137)} 在本期**唯一**的使用者 ——
 * 手工红字是人工财务动作，拒绝它不会回滚任何业务域状态机，因此可以 fail-loud；
 * 自动红字应收则永不使用 41137（D-4）。
 *
 * <p><b>边界</b>：对 {@code purchase_*} 与 {@code inventory_*} 只读（全局不变量 4，由
 * {@code FinanceReadOnlyContractTest} 静态扫描把守）；不实现历史回填（D-1）；
 * 红字应付的 schema 虽然已在 V65 就位，本期仍不提前接任何手工入口。
 */
@Service
@RequiredArgsConstructor
public class FinancePayableService {

    private final FinancePayableDao payables;
    private final FinancePayableItemDao payableItems;
    private final FinancePayableSourceDao payableSource;
    private final FinanceOperationLogRecorder operationLogs;

    /**
     * 收货确认 → 正常应付（第一批 Q9 / Q10 / Q11 / Q12）。
     *
     * <p><b>必须与触发它的事务同成败</b>，因此传播级别是 {@code MANDATORY} 而不是 {@code REQUIRED}：
     * 后者会在没有外层事务时**自己提交**，那会造出「收货单还是草稿、应付已经入账」的孤立事实，
     * 而 {@code confirm} 后续的库存写入一旦失败也没有回滚它的机会。脱离事务调用即
     * {@code IllegalTransactionStateException}，这是刻意的失败。
     *
     * <p><b>不是用户命令，因此不吃 {@code Idempotency-Key}</b>（§13）：防重复请求由
     * {@code confirm} 自身的幂等三段式与「只有 DRAFT 可确认」的状态守卫承担；
     * 防重复**事实**由库级来源唯一索引承担，命中即视为已生成并成功返回。
     *
     * <p><b>不锁任何行、不碰任何业务表</b>（§14）：业务锁已由 {@code confirm} 持有，
     * 并发的双触发由 {@code uk_finance_payable_source_active} 仲裁。
     *
     * @param purchaseReceiptId 刚被置为 {@code CONFIRMED} 的收货单 id
     */
    @Transactional(propagation = Propagation.MANDATORY, rollbackFor = Exception.class)
    public void generateOnReceiptConfirm(Long purchaseReceiptId) {
        FinancePayableSourceDto source = payableSource.selectConfirmedReceipt(purchaseReceiptId);
        if (source == null) {
            // 走到这里说明调用点把未确认（或不存在）的收货单当成收货事实，静默跳过会留下一个
            // 「业务以为财务已挂账、财务什么都没记」的缺口，因此必须失败并让收货确认一起回滚。
            throw new IllegalStateException(
                    "收货单未处于 CONFIRMED 状态，不能生成应付: " + purchaseReceiptId);
        }

        List<FinancePayableSourceLineDto> lines = payableSource.selectConfirmedReceiptLines(purchaseReceiptId);
        List<FinancePayableItemEntity> items = toItems(lines);
        BigDecimal amount = items.stream()
                .map(FinancePayableItemEntity::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (amount.signum() <= 0) {
            // 没有有效明细，或单价为 0 导致金额为 0：不产生 0 元财务事实，也不造一张空单头留痕
            // （与应收侧第二批 Q8 同纪律）。0 元事实会永久挂在待核销列表里且无法核销。
            return;
        }

        FinancePayableEntity payable = header(source, amount);
        if (payables.insertOnConflictDoNothing(payable) == 0) {
            return;
        }
        for (FinancePayableItemEntity item : items) {
            item.setPayableId(payable.getId());
            if (payableItems.insertOnConflictDoNothing(item) != 1) {
                // 单头是本次刚插入的，因此这里撞键不是重放，而是同一收货行被挂到了两张应付单上。
                throw new IllegalStateException(
                        "收货行已挂在别的应付单上，本次生成整体回滚: " + item.getSourceId());
            }
        }

        operationLogs.record(ScmFinanceBusinessTypeEnum.PAYABLE, payable.getId(),
                ScmFinanceOperationTypeEnum.GENERATE, null, null,
                generatedSnapshot(payable, items.size()));
    }

    /**
     * 应付单头。{@code originalPayableId} 与 {@code reason} 保持 {@code null} ——
     * {@code ck_finance_payable_source_pairing} 要求 NORMAL 行两者皆空。
     */
    private FinancePayableEntity header(FinancePayableSourceDto source, BigDecimal amount) {
        String operator = ScmOperator.current();
        OffsetDateTime now = OffsetDateTime.now();

        FinancePayableEntity payable = new FinancePayableEntity();
        payable.setPayableNo(ScmDocumentNumbers.format(
                FinanceConstant.PAYABLE_NO_PREFIX, payables.nextPayableNo()));
        payable.setSourceType(ScmFinancePayableSourceTypeEnum.PURCHASE_RECEIPT.name());
        payable.setSourceId(source.getPurchaseReceiptId());
        payable.setPurchaseOrderId(source.getPurchaseOrderId());
        payable.setSupplierId(source.getSupplierId());
        payable.setSupplierNameSnapshot(source.getSupplierNameSnapshot());
        payable.setEntryType(ScmFinanceEntryTypeEnum.NORMAL.name());
        payable.setAmount(amount);
        payable.setEventAt(source.getConfirmedAt());
        payable.setCreatedAt(now);
        payable.setUpdatedAt(now);
        payable.setCreatedBy(operator);
        payable.setUpdatedBy(operator);
        return payable;
    }

    /**
     * 明细装配：量取收货行的有效量、价取采购行的结算单价，金额 {@code ROUND(量 × 价, 4, HALF_UP)}
     * （第二批 Q22）。单头金额是**已按 4 位舍入的行金额之和**，不是「先求和再舍入」——
     * 后者会让单头与明细对不上账，而对账时没人能解释那半分钱的差额。
     */
    private List<FinancePayableItemEntity> toItems(List<FinancePayableSourceLineDto> lines) {
        String operator = ScmOperator.current();
        OffsetDateTime now = OffsetDateTime.now();

        return lines.stream().map(line -> {
            BigDecimal amount = line.getQuantity().multiply(line.getUnitPrice())
                    .setScale(FinanceConstant.AMOUNT_SCALE, RoundingMode.HALF_UP);

            FinancePayableItemEntity item = new FinancePayableItemEntity();
            item.setSourceType(ScmFinancePayableItemSourceTypeEnum.PURCHASE_RECEIPT_ITEM.name());
            item.setSourceId(line.getPurchaseReceiptItemId());
            item.setPurchaseOrderItemId(line.getPurchaseOrderItemId());
            item.setSkuId(line.getSkuId());
            item.setSkuNameSnapshot(line.getSkuNameSnapshot());
            item.setUnitSnapshot(line.getUnitSnapshot());
            item.setQuantity(line.getQuantity());
            item.setUnitPrice(line.getUnitPrice());
            item.setAmount(amount);
            item.setCreatedAt(now);
            item.setUpdatedAt(now);
            item.setCreatedBy(operator);
            item.setUpdatedBy(operator);
            return item;
        }).toList();
    }

    /**
     * {@code GENERATE} 的 {@code after_data}：单头快照（§17），{@code before_data} 为 {@code null}。
     *
     * <p>金额与时间落成字符串：JSONB 侧的 {@code JsonbObjectMapTypeHandler} 用的是**未注册
     * JavaTimeModule 的裸 ObjectMapper**，把 {@code OffsetDateTime} 直接放进快照会在写入时炸。
     */
    private Map<String, Object> generatedSnapshot(FinancePayableEntity payable, int itemCount) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("payableNo", payable.getPayableNo());
        snapshot.put("sourceType", payable.getSourceType());
        snapshot.put("sourceId", payable.getSourceId());
        snapshot.put("purchaseOrderId", payable.getPurchaseOrderId());
        snapshot.put("supplierId", payable.getSupplierId());
        snapshot.put("supplierNameSnapshot", payable.getSupplierNameSnapshot());
        snapshot.put("entryType", payable.getEntryType());
        snapshot.put("amount", payable.getAmount().toPlainString());
        snapshot.put("eventAt", payable.getEventAt().toString());
        snapshot.put("itemCount", itemCount);
        return snapshot;
    }
}
