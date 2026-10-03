package com.xsy.scm.payment.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xsy.scm.payment.domain.entity.PaymentRefundEntity;
import java.math.BigDecimal;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface PaymentRefundDao extends BaseMapper<PaymentRefundEntity> {

    PaymentRefundEntity lockById(@Param("id") Long id);

    PaymentRefundEntity selectByRefundNo(@Param("refundNo") String refundNo);

    /** 按业务来源取（一张售后退款单只对应一笔渠道退款，表上有唯一索引）。 */
    PaymentRefundEntity selectBySource(@Param("sourceType") String sourceType, @Param("sourceId") Long sourceId);

    PaymentRefundEntity selectByProviderRefundNo(@Param("provider") String provider,
            @Param("providerRefundNo") String providerRefundNo);

    /** 该笔交易已成功退掉的合计（判断可退余额，不用于自动推断退款额）。 */
    BigDecimal sumSucceededByTransaction(@Param("transactionId") Long transactionId);

    int markSucceeded(@Param("id") Long id, @Param("providerRefundNo") String providerRefundNo,
            @Param("operator") String operator);

    int markFailed(@Param("id") Long id, @Param("failureCode") String failureCode,
            @Param("failureMessage") String failureMessage, @Param("operator") String operator);

    int markPending(@Param("id") Long id, @Param("providerRefundNo") String providerRefundNo,
            @Param("operator") String operator);
}
