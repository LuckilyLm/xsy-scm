package com.xsy.scm.payment.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xsy.scm.common.scope.ScmDataScopeContext;
import com.xsy.scm.payment.domain.entity.PaymentTransactionEntity;
import com.xsy.scm.payment.domain.form.PaymentTransactionQueryForm;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface PaymentTransactionDao extends BaseMapper<PaymentTransactionEntity> {

    /**
     * 按渠道交易号取交易 —— **回调匹配的入口**。
     *
     * <p>
     * 渠道交易号在同一渠道内唯一（表上唯一索引），因此这里最多返回一行。
     */
    List<PaymentTransactionEntity> queryPage(Page<?> page, @Param("query") PaymentTransactionQueryForm query,
            @Param("scope") ScmDataScopeContext scope);

    PaymentTransactionEntity selectByProviderTransactionNo(@Param("provider") String provider,
            @Param("providerTransactionNo") String providerTransactionNo);

    PaymentTransactionEntity lockById(@Param("id") Long id);

    PaymentTransactionEntity selectByIntentId(@Param("intentId") Long intentId);

    /**
     * 条件更新为成功：把「当前状态」放进 WHERE，重复回调不会二次生效。
     */
    int markSucceeded(@Param("id") Long id, @Param("providerAmount") BigDecimal providerAmount,
            @Param("operator") String operator);

    int markBalanceSucceeded(@Param("id") Long id, @Param("amount") BigDecimal amount,
            @Param("paidAt") OffsetDateTime paidAt, @Param("operator") String operator);

    int markFailed(@Param("id") Long id, @Param("failureCode") String failureCode,
            @Param("failureMessage") String failureMessage, @Param("operator") String operator);

    /**
     * 某业务窗口内本地成功收款的交易（对账用）。
     */
    List<PaymentTransactionEntity> listSucceededBetween(@Param("provider") String provider,
            @Param("startAt") OffsetDateTime startAt, @Param("endAt") OffsetDateTime endAt);

    /**
     * 某业务窗口内的**全部状态**本地交易（对账用）。
     *
     * <p>
     * 不只看成功的：渠道账上有钱而本地停在 PENDING/FAILED，正是 {@code STATUS_MISMATCH}
     * 要发现的情形；只看成功交易就永远看不到它。
     */
    List<PaymentTransactionEntity> listByWindow(@Param("provider") String provider,
            @Param("startAt") OffsetDateTime startAt, @Param("endAt") OffsetDateTime endAt);
}
