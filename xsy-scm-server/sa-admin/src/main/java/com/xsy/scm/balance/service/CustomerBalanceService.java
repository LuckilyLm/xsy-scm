package com.xsy.scm.balance.service;

import com.xsy.scm.balance.constant.BalanceErrorCode;
import com.xsy.scm.balance.constant.ScmBalanceDirectionEnum;
import com.xsy.scm.balance.constant.ScmBalanceMovementTypeEnum;
import com.xsy.scm.balance.constant.ScmBalanceSourceTypeEnum;
import com.xsy.scm.balance.dao.CustomerBalanceAccountDao;
import com.xsy.scm.balance.dao.CustomerBalanceMovementDao;
import com.xsy.scm.balance.domain.entity.CustomerBalanceAccountEntity;
import com.xsy.scm.balance.domain.entity.CustomerBalanceMovementEntity;
import com.xsy.scm.balance.domain.form.BalanceCorrectionForm;
import com.xsy.scm.common.constant.ScmOperator;
import com.xsy.scm.common.exception.ScmBusinessException;
import com.xsy.scm.common.idempotency.ScmIdempotencyService;
import com.xsy.scm.common.util.ScmDocumentNumbers;
import com.xsy.scm.customer.domain.entity.CustomerEntity;
import com.xsy.scm.customer.service.CustomerService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 客户余额账本（ADM-12 3-12a）。
 *
 * <p>
 * <b>余额不是维护出来的，是推导出来的</b>：{@code SUM(CREDIT) − SUM(DEBIT)}。
 * 账户表只提供并发锁锚点，没有任何「当前余额」字段可写。
 *
 * <p>
 * <b>扣款顺序即纪律</b>：锁账户 → 在锁内**重新汇总**余额 → 判断够不够 → 落 DEBIT。
 * 少了「锁内重新汇总」这一步，两笔并发支付会各自读到「够」，然后各扣一次，把余额花成负数。
 *
 * <p>
 * <b>本片刻意不接的东西</b>：{@code PaymentIntent.BALANCE} 的推进、余额消费到 Finance 的映射、
 * 余额退款的资金事实 —— 那三件都要先决定「余额消费与应收核销、与 write-off 的关系」，
 * 属 3-12b / 3-12c。这里只把账本、并发、防重、查询与更正做干净。
 */
@Service
@RequiredArgsConstructor
public class CustomerBalanceService {

    private static final int SCALE = 4;

    private static final String MOVEMENT_NO_PREFIX = "CBM";

    private static final String CORRECTION_SCOPE = "BALANCE_CORRECTION";

    private final CustomerBalanceAccountDao customerBalanceAccountDao;

    private final CustomerBalanceMovementDao customerBalanceMovementDao;

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

    /**
     * 扣款：余额消费。
     *
     * <p>
     * 顺序不可调换：**先锁账户，再在锁内重新汇总**。汇总必须在锁之后做，
     * 锁之前读到的是过期快照 —— 那正是并发超支的成因。
     *
     * @param customerId
     *            本次业务实际发生的客户；钱包取它的结算主体
     * @param amount
     *            扣减金额，必须为正
     */
    @Transactional(rollbackFor = Exception.class)
    public CustomerBalanceMovementEntity consume(Long customerId, BigDecimal amount, String reason) {
        BigDecimal value = positiveAmount(amount);
        CustomerEntity settlement = settlementCustomerOf(customerId);
        CustomerBalanceAccountEntity account = lockAccount(settlement);
        BigDecimal available = signedBalance(account.getId());
        if (available.compareTo(value) < 0) {
            // **不允许负余额**：宁可失败也不透支，库层没有余额列所以只能在这里挡住
            throw new ScmBusinessException(BalanceErrorCode.BALANCE_INSUFFICIENT);
        }
        return record(account, customerId, ScmBalanceMovementTypeEnum.CONSUME,
                ScmBalanceDirectionEnum.DEBIT, value, null, null, reason);
    }

    /**
     * 入账：充值 / 退款返还。**由 3-12b / 3-12c 驱动**，本片只提供账本原语。
     *
     * <p>
     * 必须带业务来源：{@code RECHARGE} 用 {@code PAYMENT_TRANSACTION + transactionId}、
     * {@code REFUND} 用 {@code ORDER_REFUND + orderRefundId}。来源唯一索引保证
     * 「同一笔支付交易只充值一次、同一张退款单只返还一次」—— 重复回调不会重复入账。
     *
     * <p>
     * 重复驱动**静默返回已有流水**而不是报错：回调重投是常态，报错会让渠道一直重试。
     */
    @Transactional(rollbackFor = Exception.class)
    public CustomerBalanceMovementEntity credit(ScmBalanceMovementTypeEnum type, Long customerId, BigDecimal amount,
            ScmBalanceSourceTypeEnum sourceType, Long sourceId, String reason) {
        if (!type.directionSelectable() && type != ScmBalanceMovementTypeEnum.RECHARGE
                && type != ScmBalanceMovementTypeEnum.REFUND) {
            throw new ScmBusinessException(BalanceErrorCode.BALANCE_TYPE_INVALID);
        }
        if (sourceType == null || sourceId == null) {
            throw new ScmBusinessException(BalanceErrorCode.BALANCE_SOURCE_INVALID);
        }
        CustomerBalanceMovementEntity existing = customerBalanceMovementDao.selectBySource(sourceType.name(), sourceId);
        if (existing != null) {
            return existing;
        }
        BigDecimal value = positiveAmount(amount);
        CustomerEntity settlement = settlementCustomerOf(customerId);
        CustomerBalanceAccountEntity account = lockAccount(settlement);
        return record(account, customerId, type, type.getFixedDirection(), value, sourceType, sourceId, reason);
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
                ScmBalanceMovementTypeEnum.CORRECTION, direction, value, null, null, reason);
        idempotencyService.complete(claim, "BALANCE_MOVEMENT", movement.getId(), movement);
        return movement;
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
            ScmBalanceSourceTypeEnum sourceType, Long sourceId, String reason) {
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
        // 发生时刻由调用方语义决定；当前阶段就是写入时刻（充值/退款接入时会换成渠道时点）
        movement.setOccurredAt(now);
        movement.setCreatedAt(now);
        movement.setUpdatedAt(now);
        movement.setCreatedBy(operator);
        movement.setUpdatedBy(operator);
        customerBalanceMovementDao.insert(movement);
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
        try {
            customerBalanceAccountDao.insert(account);
        } catch (org.springframework.dao.DuplicateKeyException exception) {
            // 并发建账户：唯一索引仲裁。重新加锁读回，让调用方拿到的仍是同一条被锁住的行。
            CustomerBalanceAccountEntity raced = customerBalanceAccountDao
                    .lockBySettlementCustomerId(settlement.getId());
            if (raced == null) {
                throw exception;
            }
            return raced;
        }
        return customerBalanceAccountDao.lockBySettlementCustomerId(settlement.getId());
    }

    /** 余额 = SUM(CREDIT) − SUM(DEBIT)。方向字面量由枚举提供，SQL 里不写死。 */
    private BigDecimal signedBalance(Long accountId) {
        BigDecimal value = customerBalanceMovementDao.sumSignedByAccount(accountId,
                ScmBalanceDirectionEnum.CREDIT.name());
        return value == null ? BigDecimal.ZERO.setScale(SCALE) : value.setScale(SCALE, RoundingMode.HALF_UP);
    }

    private static BigDecimal positiveAmount(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            // 金额恒正：方向由 direction 表达，0 或负数没有意义
            throw new ScmBusinessException(BalanceErrorCode.BALANCE_AMOUNT_INVALID);
        }
        return amount.setScale(SCALE, RoundingMode.HALF_UP);
    }
}
