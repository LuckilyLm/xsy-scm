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
 * 应收域服务。
 *
 * <p><b>F1-2C 在此实现</b>：退货批准 → 红字应收，以及「先退后签」时由签收补生成红字（设计稿 §8.2）。
 * 红字一律按 {@code order_return_item.approved_amount} 全额生成，**不**扣已核销额、**不**封顶、
 * **不**静默丢差额，也**永不**抛 {@code FINANCE_RED_AMOUNT_EXCEEDED(41137)}（D-2 / D-4）——
 * 生成器抛错会让 {@code OrderReturn approve} 整笔回滚，等于财务反向控制订单域状态机。
 *
 * <p><b>边界</b>：对 {@code sales_order*} / {@code order_return*} / {@code inventory_*} /
 * {@code delivery_*} 只读（全局不变量 4，由 {@code FinanceReadOnlyContractTest} 静态扫描把守）；
 * 不实现历史回填（D-1）；不做任何金额上限校验（上限属 F1-4 的手工红字应付）。
 */
@Service
@RequiredArgsConstructor
public class FinanceReceivableService {

    private final FinanceReceivableDao receivables;
    private final FinanceReceivableItemDao receivableItems;
    private final FinanceReceivableSourceDao receivableSource;
    private final FinanceOperationLogRecorder operationLogs;

    /**
     * 签收 → 正常应收（第一批 Q1 / Q2 / Q3 / Q4，第二批 Q5 / Q6 / Q8）。
     *
     * <p><b>必须与签收同事务同成败</b>，因此传播级别是 {@code MANDATORY}（与应付生成器同纪律）：
     * {@code REQUIRED} 会在没有外层事务时自己提交，造出「配送侧还没签收、应收已经入账」的孤立事实。
     *
     * <p><b>只在 {@code SIGNED} 分支调用</b>：{@code markSigned} 是同一条 UPDATE 服务两种结果，
     * {@code EXCEPTION} 不形成应收（第二批 Q6），而库存侧的 {@code SALES_OUT} 保持原样不反冲。
     *
     * <p><b>不是用户命令，因此不吃 {@code Idempotency-Key}</b>：防重复事实由
     * {@code uk_finance_receivable_source_active} 仲裁，命中即按已生成成功返回且不留第二条日志。
     * 生成器不锁任何行（签收的行锁已由调用方持有）。
     *
     * @param deliveryRouteOrderId 刚被置为 {@code SIGNED} 的 {@code delivery_route_order.id}；
     *                             时点与操作人只能从这一行读，不能由调用方现取
     */
    @Transactional(propagation = Propagation.MANDATORY, rollbackFor = Exception.class)
    public void generateOnSign(Long deliveryRouteOrderId) {
        FinanceReceivableSourceDto source = receivableSource.selectSignedAssignment(deliveryRouteOrderId);
        if (source == null) {
            // 调用点把没签收（或不存在）的分配行当成签收事实。静默跳过会留下「配送以为已签收入账、
            // 财务什么都没记」的缺口，因此必须失败并让签收一起回滚。
            throw new IllegalStateException(
                    "配送分配行未处于 SIGNED 状态，不能生成应收: " + deliveryRouteOrderId);
        }

        List<FinanceReceivableItemEntity> items = toItems(source,
                receivableSource.selectOutboundLines(source.getSalesOrderId()));
        BigDecimal amount = items.stream()
                .map(FinanceReceivableItemEntity::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (amount.signum() <= 0) {
            // 两种形状都在这里跳过：① 签收成功但零实发（第二批 Q8）；② 单价全为 0 导致金额为 0。
            // 跳过是成功语义，绝不为此回滚合法签收，也绝不造空单头或 0 元事实留痕。
            return;
        }

        FinanceReceivableEntity receivable = header(source, amount);
        if (receivables.insertOnConflictDoNothing(receivable) == 0) {
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
                generatedSnapshot(receivable, items.size()));
    }

    /**
     * 应收单头。{@code originalReceivableId} 与 {@code reason} 保持 {@code null} ——
     * {@code ck_finance_receivable_entry_pairing} 要求 NORMAL 行的 {@code originalReceivableId} 为空。
     *
     * <p>{@code createdBy / updatedBy} 取已落库的 {@code signed_by}，而不是在服务层重取一次身份：
     * 签收人是这份事实的一部分，不是「谁触发了这次写入」的副产品。
     */
    private FinanceReceivableEntity header(FinanceReceivableSourceDto source, BigDecimal amount) {
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
     * 明细装配：量取出库行、价取订单行的冻结售价，金额 {@code ROUND(量 × 价, 4, HALF_UP)}
     * （第二批 Q22）。单头金额是**已按 4 位舍入的行金额之和**，不是「先求和再舍入」。
     *
     * <p>一条订单行对应多条出库行时逐条成行，不合并、不去重（V63 刻意不为 {@code sales_order_item_id}
     * 建唯一索引），{@code finance_receivable_item.amount} 的 CHECK 是 {@code >= 0}，
     * 因此合法的 0 元明细照实入账——丢掉它就是少算应收。
     */
    private List<FinanceReceivableItemEntity> toItems(FinanceReceivableSourceDto source,
                                                      List<FinanceReceivableSourceLineDto> lines) {
        OffsetDateTime now = OffsetDateTime.now();

        return lines.stream().map(line -> {
            BigDecimal amount = line.getQuantity().multiply(line.getUnitPrice())
                    .setScale(FinanceConstant.AMOUNT_SCALE, RoundingMode.HALF_UP);

            FinanceReceivableItemEntity item = new FinanceReceivableItemEntity();
            item.setSourceType(ScmFinanceReceivableItemSourceTypeEnum.INVENTORY_OUTBOUND_ITEM.name());
            item.setSourceId(line.getInventoryOutboundItemId());
            item.setOrderItemId(line.getSalesOrderItemId());
            item.setSkuId(line.getSkuId());
            item.setSkuNameSnapshot(line.getSkuNameSnapshot());
            item.setUnitSnapshot(line.getUnitSnapshot());
            item.setQuantity(line.getQuantity());
            item.setUnitPrice(line.getUnitPrice());
            item.setAmount(amount);
            item.setCreatedAt(now);
            item.setUpdatedAt(now);
            item.setCreatedBy(source.getSignedBy());
            item.setUpdatedBy(source.getSignedBy());
            return item;
        }).toList();
    }

    /**
     * {@code GENERATE} 的 {@code after_data}：单头快照（§17），{@code before_data} 为 {@code null}。
     *
     * <p>金额与时间落成字符串：JSONB 侧的 {@code JsonbObjectMapTypeHandler} 用的是**未注册
     * JavaTimeModule 的裸 ObjectMapper**，把 {@code OffsetDateTime} 直接放进快照会在写入时炸。
     */
    private Map<String, Object> generatedSnapshot(FinanceReceivableEntity receivable, int itemCount) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("receivableNo", receivable.getReceivableNo());
        snapshot.put("sourceType", receivable.getSourceType());
        snapshot.put("orderId", receivable.getOrderId());
        snapshot.put("customerId", receivable.getCustomerId());
        snapshot.put("customerNameSnapshot", receivable.getCustomerNameSnapshot());
        snapshot.put("entryType", receivable.getEntryType());
        snapshot.put("amount", receivable.getAmount().toPlainString());
        snapshot.put("eventAt", receivable.getEventAt().toString());
        snapshot.put("itemCount", itemCount);
        return snapshot;
    }
}
