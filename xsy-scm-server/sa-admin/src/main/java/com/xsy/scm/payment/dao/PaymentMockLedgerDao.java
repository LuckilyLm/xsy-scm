package com.xsy.scm.payment.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xsy.scm.payment.domain.entity.PaymentMockLedgerEntity;
import java.time.LocalDate;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 本地模拟渠道的账本。
 *
 * <p>
 * 只有 {@code MockPaymentProvider} 写它，且写在**独立事务**里（渠道是另一个系统，
 * 它的账不随本地事务回滚）。业务域只在对账时通过 provider 契约读汇总，不直接查这张表。
 */
@Mapper
public interface PaymentMockLedgerDao extends BaseMapper<PaymentMockLedgerEntity> {

    PaymentMockLedgerEntity selectInByProviderTransactionNo(@Param("providerTransactionNo") String no);

    List<PaymentMockLedgerEntity> listByBizDate(@Param("bizDate") LocalDate bizDate);
}
