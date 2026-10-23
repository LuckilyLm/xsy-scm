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
import com.xsy.scm.payment.support.RefundBalanceSink;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.Objects;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 客户余额只追加流水；写之前先锁账户行，再查重和重算可用余额。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CustomerBalanceService implements BalanceRechargeSink, BalanceConsumptionSink, RefundBalanceSink {

    private static final int SCALE = 4;

    private static final String MOVEMENT_NO_PREFIX = "CBM";

    private static final String CORRECTION_SCOPE = "BALANCE_CORRECTION";

    private final CustomerBalanceAccountDao customerBalanceAccountDao;

    private final CustomerBalanceMovementDao customerBalanceMovementDao;

    private final CustomerBalanceRechargeDao customerBalanceRechargeDao;

    private final CustomerService customerService;

    private final ScmIdempotencyService idempotencyService;

    /** 返回客户的结算主体，也就是余额账户所有者。 */
    @Transactional(readOnly = true)
    public CustomerEntity settlementCustomerOf(Long customerId) {
        CustomerEntity customer = customerService.require(customerId);
        return customerService.requireSettlementAccount(customer);
    }

    /**
     * 余额只读，不创建账户。没有账户就是 0 元：没开户和余额为空在金额上一样。
     */
    @Transactional(readOnly = true)
    public BigDecimal balanceOf(Long customerId) {
        return balanceOfSettlement(settlementCustomerOf(customerId).getId());
    }

    /**
     * 按结算主体读余额，供查询层复用，少解析一次客户关系。
     */
    @Transactional(readOnly = true)
    public BigDecimal balanceOfSettlement(Long settlementCustomerId) {
        CustomerBalanceAccountEntity account = customerBalanceAccountDao
            .selectBySettlementCustomerId(settlementCustomerId);
        return account == null ? BigDecimal.ZERO.setScale(SCALE) : signedBalance(account.getId());
    }

    /**
     * 订单用余额支付：锁内查重，再算一遍可用余额。
     */
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
        CustomerBalanceMovementEntity movement = customerBalanceMovementDao
            .selectBySource(ScmBalanceSourceTypeEnum.PAYMENT_INTENT.name(), intentId);
        if (movement == null) {
            if (signedBalance(account.getId()).compareTo(value) < 0) {
                throw new ScmBusinessException(BalanceErrorCode.BALANCE_INSUFFICIENT);
            }
            movement = record(account, customerId, ScmBalanceMovementTypeEnum.CONSUME, ScmBalanceDirectionEnum.DEBIT,
                value, ScmBalanceSourceTypeEnum.PAYMENT_INTENT, intentId, "订单余额支付", OffsetDateTime.now());
        }
        requireMatchingMovement(movement, account, customerId, ScmBalanceMovementTypeEnum.CONSUME,
            ScmBalanceDirectionEnum.DEBIT, value);
        return new BalanceConsumptionResult(movement.getId(), intentId, customerId, settlementCustomerId,
            movement.getAmount(), movement.getOccurredAt());
    }

    /**
     * 退回原消费的那个余额账户，不按客户现在的集团关系重新解析。
     */
    @Transactional(propagation = Propagation.MANDATORY, rollbackFor = Exception.class)
    public CustomerBalanceMovementEntity refundToSettlement(Long refundId, Long customerId, Long settlementCustomerId,
        BigDecimal amount, OffsetDateTime occurredAt) {
        if (refundId == null || customerId == null || settlementCustomerId == null || occurredAt == null) {
            throw new ScmBusinessException(BalanceErrorCode.BALANCE_REFUND_SOURCE_INVALID);
        }
        BigDecimal value = positiveAmount(amount);
        CustomerBalanceAccountEntity account = customerBalanceAccountDao
            .lockBySettlementCustomerId(settlementCustomerId);
        if (account == null) {
            throw new ScmBusinessException(BalanceErrorCode.BALANCE_REFUND_SOURCE_INVALID);
        }
        var existing = customerBalanceMovementDao.selectBySource(ScmBalanceSourceTypeEnum.ORDER_REFUND.name(),
            refundId);
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

    /**
     * 余额返还失败时回滚到保存点，外层订单事务继续把该分摊记为失败。
     */
    @Override
    @Transactional(propagation = Propagation.NESTED, rollbackFor = Exception.class)
    public Long refundAllocation(Long allocationId, Long customerId, Long settlementCustomerId, BigDecimal amount,
        OffsetDateTime occurredAt) {
        if (allocationId == null || customerId == null || settlementCustomerId == null || occurredAt == null) {
            throw new ScmBusinessException(BalanceErrorCode.BALANCE_REFUND_SOURCE_INVALID);
        }
        CustomerBalanceAccountEntity account = customerBalanceAccountDao
            .lockBySettlementCustomerId(settlementCustomerId);
        if (account == null) {
            throw new ScmBusinessException(BalanceErrorCode.BALANCE_REFUND_SOURCE_INVALID);
        }
        CustomerBalanceMovementEntity movement = creditAt(ScmBalanceMovementTypeEnum.REFUND, customerId,
            settlementCustomerId, account.getSettlementCustomerNameSnapshot(), amount,
            ScmBalanceSourceTypeEnum.REFUND_FUNDING_ALLOCATION, allocationId, "售后资金分摊返还", occurredAt);
        return movement.getId();
    }

    private CustomerBalanceMovementEntity creditAt(ScmBalanceMovementTypeEnum type, Long customerId,
        Long settlementCustomerId, String settlementCustomerName, BigDecimal amount,
        ScmBalanceSourceTypeEnum sourceType, Long sourceId, String reason, OffsetDateTime occurredAt) {
        if (!((type == ScmBalanceMovementTypeEnum.RECHARGE
            && sourceType == ScmBalanceSourceTypeEnum.PAYMENT_TRANSACTION)
            || (type == ScmBalanceMovementTypeEnum.REFUND && (sourceType == ScmBalanceSourceTypeEnum.ORDER_REFUND
                || sourceType == ScmBalanceSourceTypeEnum.REFUND_FUNDING_ALLOCATION)))
            || sourceId == null) {
            throw new ScmBusinessException(BalanceErrorCode.BALANCE_SOURCE_INVALID);
        }
        BigDecimal value = positiveAmount(amount);
        CustomerBalanceAccountEntity account = lockAccount(settlementCustomerId, settlementCustomerName);
        CustomerBalanceMovementEntity existing = customerBalanceMovementDao.selectBySource(sourceType.name(), sourceId);
        if (existing != null) {
            requireMatchingMovement(existing, account, customerId, type, type.getFixedDirection(), value);
            return existing;
        }
        return record(account, customerId, type, type.getFixedDirection(), value, sourceType, sourceId, reason,
            occurredAt);
    }

    /**
     * 人工更正：对账后修正余额。
     *
     * <p>
     * 更正也是追加一条流水，历史不可改。
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

        CustomerEntity customer = customerService.requireInCurrentScope(form.getCustomerId());
        CustomerEntity settlement = customerService.requireSettlementAccount(customer);
        customerService.requireInCurrentScope(settlement.getId());

        var claim = idempotencyService.claim(CORRECTION_SCOPE + ":" + form.getCustomerId(), idempotencyKey, form);
        if (claim.replay()) {
            return idempotencyService.replay(claim, CustomerBalanceMovementEntity.class);
        }

        CustomerBalanceAccountEntity account = lockAccount(settlement);
        if (direction == ScmBalanceDirectionEnum.DEBIT) {
            // 更正也不能扣成负数，否则「不允许负余额」这条规则可以被绕过
            BigDecimal available = signedBalance(account.getId());
            if (available.compareTo(value) < 0) {
                throw new ScmBusinessException(BalanceErrorCode.BALANCE_INSUFFICIENT);
            }
        }
        CustomerBalanceMovementEntity movement = record(account, form.getCustomerId(),
            ScmBalanceMovementTypeEnum.CORRECTION, direction, value, null, null, reason, OffsetDateTime.now());
        idempotencyService.complete(claim, ScmBalanceIdempotencyResourceTypeEnum.BALANCE_MOVEMENT.name(),
            movement.getId(), movement);
        return movement;
    }

    /**
     * 充值成功后按渠道实际到账金额入账；不一致留待对账，不按申请金额记。
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
            // 充值的客户和支付意图的客户不一致：来源对不上，宁可失败
            throw new ScmBusinessException(BalanceErrorCode.BALANCE_SOURCE_INVALID);
        }
        if (providerAmount.compareTo(recharge.getAmount()) != 0) {
            log.warn("充值实收与申请金额不一致，按实收入账：rechargeNo={} 申请={} 实收={}", recharge.getRechargeNo(), recharge.getAmount(),
                providerAmount);
        }
        if (recharge.getSettlementCustomerId() == null || recharge.getSettlementCustomerNameSnapshot() == null
            || succeededAt == null) {
            throw new ScmBusinessException(BalanceErrorCode.BALANCE_SOURCE_INVALID);
        }
        creditAt(ScmBalanceMovementTypeEnum.RECHARGE, customerId, recharge.getSettlementCustomerId(),
            recharge.getSettlementCustomerNameSnapshot(), providerAmount, ScmBalanceSourceTypeEnum.PAYMENT_TRANSACTION,
            transactionId, "在线充值 " + recharge.getRechargeNo(), succeededAt);
    }

    /**
     * 写一条流水，所有入账都走这里。
     *
     * <p>
     * 方向与类型在这里校验，数据库的 {@code ck_customer_balance_movement_type_direction} 兜底；充值 / 消费 / 退款返还的方向是固定的，不接受调用方改写。
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
        if (type == ScmBalanceMovementTypeEnum.CORRECTION && (reason == null || reason.trim().isEmpty())) {
            throw new ScmBusinessException(BalanceErrorCode.BALANCE_REASON_REQUIRED);
        }
        OffsetDateTime now = OffsetDateTime.now();
        String operator = ScmOperator.current();
        CustomerBalanceMovementEntity movement = new CustomerBalanceMovementEntity();
        movement
            .setMovementNo(ScmDocumentNumbers.format(MOVEMENT_NO_PREFIX, customerBalanceMovementDao.nextMovementNo()));
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
            CustomerBalanceMovementEntity existing = customerBalanceMovementDao.selectBySource(sourceType.name(),
                sourceId);
            requireMatchingMovement(existing, account, customerId, type, direction, amount);
            return existing;
        }
        return movement;
    }

    /**
     * 取余额账户，必要时创建，然后加锁。
     *
     * <p>
     * 只在动钱时创建，读余额不写库。唯一索引保证一个结算主体只有一个账户；并发冲突后读回已有行。
     */
    private CustomerBalanceAccountEntity lockAccount(CustomerEntity settlement) {
        return lockAccount(settlement.getId(), settlement.getName());
    }

    private CustomerBalanceAccountEntity lockAccount(Long settlementCustomerId, String settlementCustomerName) {
        CustomerBalanceAccountEntity existing = customerBalanceAccountDao
            .lockBySettlementCustomerId(settlementCustomerId);
        if (existing != null) {
            return existing;
        }
        OffsetDateTime now = OffsetDateTime.now();
        String operator = ScmOperator.current();
        CustomerBalanceAccountEntity account = new CustomerBalanceAccountEntity();
        account.setSettlementCustomerId(settlementCustomerId);
        account.setSettlementCustomerNameSnapshot(settlementCustomerName);
        account.setCreatedAt(now);
        account.setUpdatedAt(now);
        account.setCreatedBy(operator);
        account.setUpdatedBy(operator);
        customerBalanceAccountDao.insertOnConflictDoNothing(account);
        return customerBalanceAccountDao.lockBySettlementCustomerId(settlementCustomerId);
    }

    private static void requireMatchingMovement(CustomerBalanceMovementEntity movement,
        CustomerBalanceAccountEntity account, Long customerId, ScmBalanceMovementTypeEnum type,
        ScmBalanceDirectionEnum direction, BigDecimal amount) {
        if (movement == null || !Objects.equals(movement.getAccountId(), account.getId())
            || !Objects.equals(movement.getSettlementCustomerId(), account.getSettlementCustomerId())
            || !Objects.equals(movement.getCustomerId(), customerId) || !type.name().equals(movement.getType())
            || !direction.name().equals(movement.getDirection()) || movement.getAmount().compareTo(amount) != 0) {
            throw new ScmBusinessException(BalanceErrorCode.BALANCE_SOURCE_INVALID);
        }
    }

    /**
     * 余额 = SUM(CREDIT) − SUM(DEBIT)；方向名从枚举取，不写死在 SQL 里。
     */
    private BigDecimal signedBalance(Long accountId) {
        BigDecimal value = customerBalanceMovementDao.sumSignedByAccount(accountId,
            ScmBalanceDirectionEnum.CREDIT.name());
        return value == null ? BigDecimal.ZERO.setScale(SCALE) : value.setScale(SCALE, RoundingMode.HALF_UP);
    }

    private static BigDecimal positiveAmount(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0 || amount.scale() > SCALE) {
            // 金额恒正，方向由 direction 表达；0 或负数没意义
            throw new ScmBusinessException(BalanceErrorCode.BALANCE_AMOUNT_INVALID);
        }
        return amount.setScale(SCALE, RoundingMode.HALF_UP);
    }
}
