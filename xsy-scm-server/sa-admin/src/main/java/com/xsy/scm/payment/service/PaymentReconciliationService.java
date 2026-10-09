package com.xsy.scm.payment.service;

import com.xsy.scm.common.constant.ScmOperator;
import com.xsy.scm.common.exception.ScmBusinessException;
import com.xsy.scm.payment.constant.PaymentErrorCode;
import com.xsy.scm.payment.constant.ScmPaymentReconciliationCategoryEnum;
import com.xsy.scm.payment.constant.ScmPaymentReconciliationStatusEnum;
import com.xsy.scm.payment.constant.ScmPaymentTransactionStatusEnum;
import com.xsy.scm.payment.dao.PaymentReconciliationDao;
import com.xsy.scm.payment.dao.PaymentReconciliationItemDao;
import com.xsy.scm.payment.dao.PaymentTransactionDao;
import com.xsy.scm.payment.domain.entity.PaymentReconciliationEntity;
import com.xsy.scm.payment.domain.entity.PaymentReconciliationItemEntity;
import com.xsy.scm.payment.domain.entity.PaymentTransactionEntity;
import com.xsy.scm.payment.provider.ScmPaymentProvider;
import com.xsy.scm.payment.provider.ScmPaymentProviderRegistry;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 对账服务：比较渠道账与本地账，把差异冻成 {@code payment_reconciliation_item} 事实供人工处理。
 *
 * <p>
 * 首版只发现差异，绝不自动修复 —— 自动改交易状态会让审计链说不清「这条记录是渠道说的，还是对账程序改的」。 分类：{@code LOCAL_MISSING}（渠道有本地无，最危险）/ {@code PROVIDER_MISSING}
 * / {@code AMOUNT_MISMATCH} / {@code STATUS_MISMATCH}。
 *
 * <p>
 * 本地侧取全部状态的交易，不只看成功的：只看成功交易就永远发现不了 {@code STATUS_MISMATCH}。 首版只对收款；退款对账口径不对称，是后续独立一项。
 */
@Service
@RequiredArgsConstructor
public class PaymentReconciliationService {

    private static final int SCALE = 4;

    /** 业务日按 Asia/Shanghai 切，与单号日期段同一个时区口径。 */
    private static final ZoneId BIZ_ZONE = ZoneId.of("Asia/Shanghai");

    private final PaymentReconciliationDao paymentReconciliationDao;

    private final PaymentReconciliationItemDao paymentReconciliationItemDao;

    private final PaymentTransactionDao paymentTransactionDao;

    private final PaymentNumberGenerator paymentNumberGenerator;

    private final ScmPaymentProviderRegistry providerRegistry;

    /**
     * 执行一次对账。
     *
     * <p>
     * 同一渠道同一业务日<b>只出一次结论</b>：允许重复生成会得到两份互相矛盾的结论，而后台没法判断该信哪一份。
     */
    @Transactional(rollbackFor = Exception.class)
    public PaymentReconciliationEntity run(String providerCode, LocalDate bizDate) {
        ScmPaymentProvider provider = providerRegistry.require(providerCode);
        if (paymentReconciliationDao.selectByProviderAndDate(providerCode, bizDate) != null) {
            throw new ScmBusinessException(PaymentErrorCode.PAYMENT_RECONCILIATION_DUPLICATED);
        }

        ScmPaymentProvider.Settlement settlement = provider.fetchSettlement(bizDate);
        OffsetDateTime startAt = bizDate.atStartOfDay(BIZ_ZONE).toOffsetDateTime();
        OffsetDateTime endAt = bizDate.plusDays(1).atStartOfDay(BIZ_ZONE).toOffsetDateTime();
        List<PaymentTransactionEntity> locals = paymentTransactionDao.listByWindow(providerCode, startAt, endAt);
        Map<String, PaymentTransactionEntity> localByNo = new LinkedHashMap<>();
        for (PaymentTransactionEntity local : locals) {
            localByNo.put(local.getProviderTransactionNo(), local);
        }

        String operator = ScmOperator.current();
        List<PaymentReconciliationItemEntity> items = new ArrayList<>();
        Set<String> providerNos = new HashSet<>();
        BigDecimal providerTotal = BigDecimal.ZERO.setScale(SCALE);
        for (ScmPaymentProvider.SettlementLine line : settlement.lines()) {
            providerNos.add(line.providerTransactionNo());
            providerTotal = providerTotal.add(line.amount());
            PaymentTransactionEntity local = localByNo.get(line.providerTransactionNo());
            if (local == null) {
                items.add(item(ScmPaymentReconciliationCategoryEnum.LOCAL_MISSING, line.providerTransactionNo(), null,
                        null, line.amount(), null, ScmPaymentTransactionStatusEnum.SUCCEEDED.name(), operator));
                continue;
            }
            if (!ScmPaymentTransactionStatusEnum.SUCCEEDED.name().equals(local.getStatus())) {
                items.add(item(ScmPaymentReconciliationCategoryEnum.STATUS_MISMATCH, line.providerTransactionNo(),
                        local.getId(), receivedOf(local), line.amount(), local.getStatus(),
                        ScmPaymentTransactionStatusEnum.SUCCEEDED.name(), operator));
                continue;
            }
            BigDecimal receivedAmount = receivedOf(local);
            BigDecimal intentAmount = local.getAmount().setScale(SCALE, RoundingMode.HALF_UP);
            BigDecimal settlementAmount = line.amount().setScale(SCALE, RoundingMode.HALF_UP);
            boolean intentMismatch = intentAmount.compareTo(receivedAmount) != 0;
            if (intentMismatch || receivedAmount.compareTo(settlementAmount) != 0) {
                // 同时检查本地支付意图与渠道实收：渠道对账单可能与回调金额一致，
                // 但这仍不能把偏离本地应付金额的交易算作平账。
                items.add(item(ScmPaymentReconciliationCategoryEnum.AMOUNT_MISMATCH, line.providerTransactionNo(),
                        local.getId(), intentMismatch ? intentAmount : receivedAmount,
                        intentMismatch ? receivedAmount : settlementAmount, local.getStatus(),
                        ScmPaymentTransactionStatusEnum.SUCCEEDED.name(), operator));
            }
        }

        BigDecimal localTotal = BigDecimal.ZERO.setScale(SCALE);
        for (PaymentTransactionEntity local : locals) {
            if (!ScmPaymentTransactionStatusEnum.SUCCEEDED.name().equals(local.getStatus())) {
                continue;
            }
            BigDecimal received = receivedOf(local);
            localTotal = localTotal.add(received);
            if (!providerNos.contains(local.getProviderTransactionNo())) {
                items.add(item(ScmPaymentReconciliationCategoryEnum.PROVIDER_MISSING, local.getProviderTransactionNo(),
                        local.getId(), received, null, local.getStatus(), null, operator));
            }
        }

        PaymentReconciliationEntity reconciliation = new PaymentReconciliationEntity();
        reconciliation.setReconciliationNo(paymentNumberGenerator.nextReconciliationNo());
        reconciliation.setProvider(providerCode);
        reconciliation.setBizDate(bizDate);
        reconciliation.setStatus(items.isEmpty()
                ? ScmPaymentReconciliationStatusEnum.MATCHED.name()
                : ScmPaymentReconciliationStatusEnum.MISMATCHED.name());
        reconciliation.setProviderTotal(providerTotal);
        reconciliation.setLocalTotal(localTotal);
        reconciliation.setDifference(localTotal.subtract(providerTotal));
        reconciliation.setProviderCount(settlement.count());
        reconciliation.setLocalCount(locals.size());
        reconciliation.setDifferenceCount(items.size());
        // 参与比对的交易数 = 渠道侧与本地侧的<b>并集</b>（同一笔两边都有时只算一次）；
        // 平账数 = 总数 − 差异数。平不平以<b>差异条数</b>为准，不看净差额：
        // 一正一负的差异会让净差额归零，但账其实不平。
        int totalCount = unionCount(providerNos, localByNo);
        reconciliation.setTotalCount(totalCount);
        reconciliation.setMatchedCount(totalCount - items.size());
        reconciliation.setDetail(summaryOf(items));
        reconciliation.setReconciledAt(OffsetDateTime.now());
        reconciliation.setCreatedBy(operator);
        reconciliation.setUpdatedBy(operator);
        paymentReconciliationDao.insert(reconciliation);

        for (PaymentReconciliationItemEntity item : items) {
            item.setReconciliationId(reconciliation.getId());
            paymentReconciliationItemDao.insertItem(item);
        }
        return paymentReconciliationDao.selectById(reconciliation.getId());
    }

    /** 渠道侧与本地侧交易号的并集大小。 */
    private static int unionCount(Set<String> providerNos, Map<String, PaymentTransactionEntity> localByNo) {
        Set<String> union = new HashSet<>(providerNos);
        union.addAll(localByNo.keySet());
        return union.size();
    }

    /** 逐条差异明细（后台展示用）。只含异常项：平账的靠批次汇总表达，不逐笔落库。 */
    @Transactional(readOnly = true)
    public List<PaymentReconciliationItemEntity> items(Long reconciliationId) {
        return paymentReconciliationItemDao.listByReconciliation(reconciliationId);
    }

    /** 本地实际收到的金额：优先渠道回报，没回报才退回本地应付。 */
    private static BigDecimal receivedOf(PaymentTransactionEntity transaction) {
        BigDecimal value = transaction.getProviderAmount() == null
                ? transaction.getAmount()
                : transaction.getProviderAmount();
        return value.setScale(SCALE, RoundingMode.HALF_UP);
    }

    private static PaymentReconciliationItemEntity item(ScmPaymentReconciliationCategoryEnum category,
            String providerTransactionNo, Long transactionId, BigDecimal localAmount, BigDecimal providerAmount,
            String localStatus, String providerStatus, String operator) {
        PaymentReconciliationItemEntity row = new PaymentReconciliationItemEntity();
        row.setCategory(category.name());
        row.setProviderTransactionNo(providerTransactionNo);
        row.setTransactionId(transactionId);
        row.setLocalAmount(localAmount);
        row.setProviderAmount(providerAmount == null ? null : providerAmount.setScale(SCALE, RoundingMode.HALF_UP));
        row.setLocalStatus(localStatus);
        row.setProviderStatus(providerStatus);
        row.setCreatedBy(operator);
        return row;
    }

    /** 分类汇总，落进 {@code detail}，页面不必再聚合一次。 */
    private static Map<String, Object> summaryOf(List<PaymentReconciliationItemEntity> items) {
        Map<String, Object> summary = new LinkedHashMap<>();
        for (ScmPaymentReconciliationCategoryEnum category : ScmPaymentReconciliationCategoryEnum.values()) {
            long count = items.stream().filter(item -> category.name().equals(item.getCategory())).count();
            if (count > 0) {
                summary.put(category.name(), count);
            }
        }
        return summary;
    }
}
