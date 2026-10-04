package com.xsy.scm.balance.service;

import com.xsy.scm.balance.constant.BalanceErrorCode;
import com.xsy.scm.balance.constant.ScmBalanceDirectionEnum;
import com.xsy.scm.balance.constant.ScmBalanceIdempotencyResourceTypeEnum;
import com.xsy.scm.balance.constant.ScmBalanceMovementTypeEnum;
import com.xsy.scm.balance.constant.ScmBalanceSourceTypeEnum;
import com.xsy.scm.balance.dao.CustomerBalanceAccountDao;
import com.xsy.scm.balance.dao.CustomerBalanceMovementDao;
import com.xsy.scm.balance.dao.CustomerBalanceRechargeDao;
import com.xsy.scm.balance.domain.entity.CustomerBalanceAccountEntity;
import com.xsy.scm.balance.domain.entity.CustomerBalanceMovementEntity;
import com.xsy.scm.balance.domain.entity.CustomerBalanceRechargeEntity;
import com.xsy.scm.balance.domain.form.BalanceCorrectionForm;
import com.xsy.scm.common.constant.ScmOperator;
import com.xsy.scm.common.exception.ScmBusinessException;
import com.xsy.scm.common.idempotency.ScmIdempotencyService;
import com.xsy.scm.common.util.ScmDocumentNumbers;
import com.xsy.scm.customer.domain.entity.CustomerEntity;
import com.xsy.scm.customer.service.CustomerService;
import com.xsy.scm.payment.support.BalanceConsumptionResult;
import com.xsy.scm.payment.support.BalanceConsumptionSink;
import com.xsy.scm.payment.support.BalanceRechargeSink;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** 客户余额追加账本；账户行锁保护来源查重及 CREDIT - DEBIT 的重新汇总。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CustomerBalanceService implements BalanceRechargeSink, BalanceConsumptionSink {

    private static final int SCALE = 4;

    private static final String MOVEMENT_NO_PREFIX = "CBM";

    private static final String CORRECTION_SCOPE = "BALANCE_CORRECTION";

    private final CustomerBalanceAccountDao customerBalanceAccountDao;

    private final CustomerBalanceMovementDao customerBalanceMovementDao;

    private final CustomerBalanceRechargeDao customerBalanceRechargeDao;

    private final CustomerService customerService;

    private final ScmIdempotencyService idempotencyService;

    /**
     * 钱包归属：**结算主体**。
     *
     * <p>
     * 普通客户的钱包就是自己的；集团下属单位共用集团钱包。理由是应收、授信、收款早已把
     * 「实际下单客户」与「结算主体」分开 —— 若钱包挂在子客户，A 店与 B 店各有一份不能共享的
     * 余额，就与「集团统一结算」直接冲突。流水仍保留实际业务客户，因此花的是哪家店仍可追溯。
     */
    @Transactional(readOnly = true)
    public CustomerEntity settlementCustomerOf(Long customerId) {
        CustomerEntity customer = customerService.require(customerId);
        return customerService.requireSettlementAccount(customer);
    }

    /**
     * 钱包余额（只读）。
     *
     * <p>
     * <b>不创建账户</b>：读路径不该写库。没有账户就是 0 元 —— 「还没有钱包」与「钱包是空的」
     * 在金额上等价，不必为此落一行。
     */
    @Transactional(readOnly = true)
    public BigDecimal balanceOf(Long customerId) {
        return balanceOfSettlement(settlementCustomerOf(customerId).getId());
    }

    /**
     * 按**结算主体**读余额（供查询层复用）。
     *
     * <p>
     * 查询层已经解析过结算主体，再走一遍 {@link #balanceOf} 会重复解析客户关系 ——
     * 而解析里含「客户必须合法可结算」这类校验，重复调用只会多一次查询、不改结果。
     */
    @Transactional(readOnly = true)
    public BigDecimal balanceOfSettlement(Long settlementCustomerId) {
        CustomerBalanceAccountEntity account = customerBalanceAccountDao
                .selectBySettlementCustomerId(settlementCustomerId);
        return account == null ? BigDecimal.ZERO.setScale(SCALE) : signedBalance(account.getId());
    }

    /** 订单余额消费必须带支付来源，锁内查重并重新汇总可用权益。 */
    @Override
    @Transactional(propagation = Propagation.MANDATORY, rollbackFor = Exception.class)
    public BalanceConsumptionResult consumeForPayment(Long intentId, Long customerId, Long settlementCustomerId,
            BigDecimal amount) {
        if (intentId == null || customerId == null || settlementCustomerId == null) {
            throw new ScmBusinessException(BalanceErrorCode.BALANCE_SOURCE_INVALID);
        }
        BigDecimal value = positiveAmount(amount);
        CustomerEntity settlement = settlementCustomerOf(customerId);
        if (!settlementCustomerId.equals(settlement.getId())) {
            throw new ScmBusinessException(BalanceErrorCode.BALANCE_SOURCE_INVALID);
        }
        CustomerBalanceAccountEntity account = lockAccount(settlement);
        CustomerBalanceMovementEntity movement = customerBalanceMovementDao.selectBySource(
                ScmBalanceSourceTypeEnum.PAYMENT_INTENT.name(), intentId);
        if (movement == null) {
            if (signedBalance(account.getId()).compareTo(value) < 0) {
                throw new ScmBusinessException(BalanceErrorCode.BALANCE_INSUFFICIENT);
            }
            movement = record(account, customerId, ScmBalanceMovementTypeEnum.CONSUME,
                    ScmBalanceDirectionEnum.DEBIT, value, ScmBalanceSourceTypeEnum.PAYMENT_INTENT, intentId,
                    "订单余额支付", OffsetDateTime.now());
        }
        requireMatchingMovement(movement, account, customerId, ScmBalanceMovementTypeEnum.CONSUME,
                ScmBalanceDirectionEnum.DEBIT, value);
        return new BalanceConsumptionResult(movement.getId(), intentId, customerId, settlementCustomerId,
                movement.getAmount(), movement.getOccurredAt());
    }

    /** 返还原消费所属钱包，不随客户当前集团关系重新解析归属。 */
    @Transactional(propagation = Propagation.MANDATORY, rollbackFor = Exception.class)
    public CustomerBalanceMovementEntity refundToSettlement(Long refundId, Long customerId,
            Long settlementCustomerId, BigDecimal amount, OffsetDateTime occurredAt) {
        if (refundId == null || customerId == null || settlementCustomerId == null || occurredAt == null) {
            throw new ScmBusinessException(BalanceErrorCode.BALANCE_REFUND_SOURCE_INVALID);
        }
        BigDecimal value = positiveAmount(amount);
        CustomerBalanceAccountEntity account = customerBalanceAccountDao.lockBySettlementCustomerId(settlementCustomerId);
        if (account == null) {
            throw new ScmBusinessException(BalanceErrorCode.BALANCE_REFUND_SOURCE_INVALID);
        }
        var existing = customerBalanceMovementDao.selectBySource(ScmBalanceSourceTypeEnum.ORDER_REFUND.name(), refundId);
        if (existing != null) {
            requireMatchingMovement(existing, account, customerId, ScmBalanceMovementTypeEnum.REFUND,
                    ScmBalanceDirectionEnum.CREDIT, value);
            if (!occurredAt.isEqual(existing.getOccurredAt())) {
                throw new ScmBusinessException(BalanceErrorCode.BALANCE_REFUND_SOURCE_INVALID);
            }
            return existing;
        }
        var returned = record(account, customerId, ScmBalanceMovementTypeEnum.REFUND, ScmBalanceDirectionEnum.CREDIT,
                value, ScmBalanceSourceTypeEnum.ORDER_REFUND, refundId, "售后余额返还", occurredAt);
        if (!occurredAt.isEqual(returned.getOccurredAt())) {
            throw new ScmBusinessException(BalanceErrorCode.BALANCE_REFUND_SOURCE_INVALID);
        }
        return returned;
    }

    private CustomerBalanceMovementEntity creditAt(ScmBalanceMovementTypeEnum type, Long customerId,
            BigDecimal amount, ScmBalanceSourceTypeEnum sourceType, Long sourceId, String reason,
            OffsetDateTime occurredAt) {
        if (!((type == ScmBalanceMovementTypeEnum.RECHARGE
                && sourceType == ScmBalanceSourceTypeEnum.PAYMENT_TRANSACTION)
                || (type == ScmBalanceMovementTypeEnum.REFUND
                && sourceType == ScmBalanceSourceTypeEnum.ORDER_REFUND)) || sourceId == null) {
            throw new ScmBusinessException(BalanceErrorCode.BALANCE_SOURCE_INVALID);
        }
        BigDecimal value = positiveAmount(amount);
        CustomerBalanceAccountEntity account = lockAccount(settlementCustomerOf(customerId));
        CustomerBalanceMovementEntity existing = customerBalanceMovementDao.selectBySource(sourceType.name(), sourceId);
        if (existing != null) {
            requireMatchingMovement(existing, account, customerId, type, type.getFixedDirection(), value);
            return existing;
        }
        return record(account, customerId, type, type.getFixedDirection(), value, sourceType, sourceId, reason,
                occurredAt);
    }

    /**
     * 人工更正：管理员对账后修正余额。
     *
     * <p>
     * 要求权限（Controller 层）＋ 方向 ＋ 金额 ＋ 原因 ＋ {@code Idempotency-Key} ＋ 操作人。
     * <b>没有「编辑余额」接口</b>：更正也是追加流水，历史不可改。
     */
    @Transactional(rollbackFor = Exception.class)
    public CustomerBalanceMovementEntity correct(BalanceCorrectionForm form, String idempotencyKey) {
        ScmBalanceDirectionEnum direction = ScmBalanceDirectionEnum.of(form.getDirection());
        if (direction == null) {
            throw new ScmBusinessException(BalanceErrorCode.BALANCE_DIRECTION_INVALID);
        }
        BigDecimal value = positiveAmount(form.getAmount());
        String reason = form.getReason() == null ? null : form.getReason().trim();
        if (reason == null || reason.isEmpty()) {
            throw new ScmBusinessException(BalanceErrorCode.BALANCE_REASON_REQUIRED);
        }

        var claim = idempotencyService.claim(CORRECTION_SCOPE + ":" + form.getCustomerId(), idempotencyKey, form);
        if (claim.replay()) {
            return idempotencyService.replay(claim, CustomerBalanceMovementEntity.class);
        }

        CustomerEntity settlement = settlementCustomerOf(form.getCustomerId());
        CustomerBalanceAccountEntity account = lockAccount(settlement);
        if (direction == ScmBalanceDirectionEnum.DEBIT) {
            // 更正也不能把余额扣成负数：否则「不允许负余额」这条纪律可以从更正绕过去
            BigDecimal available = signedBalance(account.getId());
            if (available.compareTo(value) < 0) {
                throw new ScmBusinessException(BalanceErrorCode.BALANCE_INSUFFICIENT);
            }
        }
        CustomerBalanceMovementEntity movement = record(account, form.getCustomerId(),
                ScmBalanceMovementTypeEnum.CORRECTION, direction, value, null, null, reason, OffsetDateTime.now());
        idempotencyService.complete(claim, ScmBalanceIdempotencyResourceTypeEnum.BALANCE_MOVEMENT.name(),
                movement.getId(),
                movement);
        return movement;
    }

    /**
     * 充值支付成功 → 钱包权益增加（ADM-12 3-12b，{@link BalanceRechargeSink} 的实现）。
     *
     * <p>
     * <b>入账金额取渠道实收</b>：钱包进多少必须等于公司真收多少。渠道实收 98 而充值申请 100 时，
     * 记 98 并留下告警 —— 与退款那条口径（不一致就不落账）刻意不同：
     * 充值场景下「客户付了钱却没有任何权益」比「权益比申请额少 2 元」严重得多。
     * 差异本身可查（充值事实仍保留申请额），留给对账处理。
     *
     * <p>
     * 幂等靠 {@code (PAYMENT_TRANSACTION, transactionId)} 的来源唯一索引：
     * 同一个回调重复投递多少次，也只产生一条充值流水。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void rechargeFromPayment(Long rechargeId, Long customerId, BigDecimal providerAmount, Long transactionId,
            OffsetDateTime succeededAt) {
        if (rechargeId == null || customerId == null || providerAmount == null || transactionId == null) {
            throw new ScmBusinessException(BalanceErrorCode.BALANCE_SOURCE_INVALID);
        }
        CustomerBalanceRechargeEntity recharge = customerBalanceRechargeDao.selectByIdForUpdate(rechargeId);
        if (recharge == null) {
            throw new ScmBusinessException(BalanceErrorCode.BALANCE_RECHARGE_NOT_FOUND);
        }
        if (!recharge.getCustomerId().equals(customerId)) {
            // 充值的业务客户与支付意图的客户不一致：来源身份已经分叉，宁可失败
            throw new ScmBusinessException(BalanceErrorCode.BALANCE_SOURCE_INVALID);
        }
        if (providerAmount.compareTo(recharge.getAmount()) != 0) {
            log.warn("充值实收与申请金额不一致，按实收入账：rechargeNo={} 申请={} 实收={}",
                    recharge.getRechargeNo(), recharge.getAmount(), providerAmount);
        }
        if (!recharge.getSettlementCustomerId().equals(settlementCustomerOf(customerId).getId())
                || succeededAt == null) {
            throw new ScmBusinessException(BalanceErrorCode.BALANCE_SOURCE_INVALID);
        }
        creditAt(ScmBalanceMovementTypeEnum.RECHARGE, customerId, providerAmount,
                ScmBalanceSourceTypeEnum.PAYMENT_TRANSACTION, transactionId,
                "在线充值 " + recharge.getRechargeNo(), succeededAt);
    }

    /**
     * 落一条流水（所有入账路径的唯一出口）。
     *
     * <p>
     * 方向与类型的一致性在这里校验，库上的 {@code ck_customer_balance_movement_type_direction}
     * 是第二层：类型固定方向的（充值 / 消费 / 退款返还）不接受调用方改写方向。
     */
    private CustomerBalanceMovementEntity record(CustomerBalanceAccountEntity account, Long customerId,
            ScmBalanceMovementTypeEnum type, ScmBalanceDirectionEnum direction, BigDecimal amount,
            ScmBalanceSourceTypeEnum sourceType, Long sourceId, String reason, OffsetDateTime occurredAt) {
        if (direction == null) {
            throw new ScmBusinessException(BalanceErrorCode.BALANCE_DIRECTION_INVALID);
        }
        if (type.getFixedDirection() != null && type.getFixedDirection() != direction) {
            throw new ScmBusinessException(BalanceErrorCode.BALANCE_DIRECTION_INVALID);
        }
        if (type == ScmBalanceMovementTypeEnum.CORRECTION
                && (reason == null || reason.trim().isEmpty())) {
            throw new ScmBusinessException(BalanceErrorCode.BALANCE_REASON_REQUIRED);
        }
        OffsetDateTime now = OffsetDateTime.now();
        String operator = ScmOperator.current();
        CustomerBalanceMovementEntity movement = new CustomerBalanceMovementEntity();
        movement.setMovementNo(ScmDocumentNumbers.format(MOVEMENT_NO_PREFIX,
                customerBalanceMovementDao.nextMovementNo()));
        movement.setAccountId(account.getId());
        movement.setSettlementCustomerId(account.getSettlementCustomerId());
        movement.setCustomerId(customerId);
        movement.setType(type.name());
        movement.setDirection(direction.name());
        movement.setAmount(amount);
        movement.setSourceType(sourceType == null ? null : sourceType.name());
        movement.setSourceId(sourceId);
        movement.setReason(reason == null || reason.trim().isEmpty() ? null : reason.trim());
        movement.setOccurredAt(occurredAt);
        movement.setCreatedAt(now);
        movement.setUpdatedAt(now);
        movement.setCreatedBy(operator);
        movement.setUpdatedBy(operator);
        if (customerBalanceMovementDao.insertOnConflictDoNothing(movement) == 0) {
            CustomerBalanceMovementEntity existing = customerBalanceMovementDao.selectBySource(sourceType.name(), sourceId);
            requireMatchingMovement(existing, account, customerId, type, direction, amount);
            return existing;
        }
        return movement;
    }

    /**
     * 取钱包账户并在必要时创建，然后**加锁**。
     *
     * <p>
     * 创建放在锁路径里而不是读路径里：读余额不该写库，而真正要动钱时必须有一个可锁的行。
     * 唯一索引保证同一结算主体只有一个账户；并发下第二个事务会命中冲突，因此先查再插，
     * 冲突时读回已有行。
     */
    private CustomerBalanceAccountEntity lockAccount(CustomerEntity settlement) {
        CustomerBalanceAccountEntity existing = customerBalanceAccountDao
                .lockBySettlementCustomerId(settlement.getId());
        if (existing != null) {
            return existing;
        }
        OffsetDateTime now = OffsetDateTime.now();
        String operator = ScmOperator.current();
        CustomerBalanceAccountEntity account = new CustomerBalanceAccountEntity();
        account.setSettlementCustomerId(settlement.getId());
        account.setSettlementCustomerNameSnapshot(settlement.getName());
        account.setCreatedAt(now);
        account.setUpdatedAt(now);
        account.setCreatedBy(operator);
        account.setUpdatedBy(operator);
        customerBalanceAccountDao.insertOnConflictDoNothing(account);
        return customerBalanceAccountDao.lockBySettlementCustomerId(settlement.getId());
    }

    private static void requireMatchingMovement(CustomerBalanceMovementEntity movement,
            CustomerBalanceAccountEntity account, Long customerId, ScmBalanceMovementTypeEnum type,
            ScmBalanceDirectionEnum direction, BigDecimal amount) {
        if (movement == null || !Objects.equals(movement.getAccountId(), account.getId())
                || !Objects.equals(movement.getSettlementCustomerId(), account.getSettlementCustomerId())
                || !Objects.equals(movement.getCustomerId(), customerId)
                || !type.name().equals(movement.getType()) || !direction.name().equals(movement.getDirection())
                || movement.getAmount().compareTo(amount) != 0) {
            throw new ScmBusinessException(BalanceErrorCode.BALANCE_SOURCE_INVALID);
        }
    }

    /** 余额 = SUM(CREDIT) − SUM(DEBIT)。方向字面量由枚举提供，SQL 里不写死。 */
    private BigDecimal signedBalance(Long accountId) {
        BigDecimal value = customerBalanceMovementDao.sumSignedByAccount(accountId,
                ScmBalanceDirectionEnum.CREDIT.name());
        return value == null ? BigDecimal.ZERO.setScale(SCALE) : value.setScale(SCALE, RoundingMode.HALF_UP);
    }

    private static BigDecimal positiveAmount(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0 || amount.scale() > SCALE) {
            // 金额恒正：方向由 direction 表达，0 或负数没有意义
            throw new ScmBusinessException(BalanceErrorCode.BALANCE_AMOUNT_INVALID);
        }
        return amount.setScale(SCALE, RoundingMode.HALF_UP);
    }
}
