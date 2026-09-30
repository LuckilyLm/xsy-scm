package com.xsy.scm.finance.dao;

import com.xsy.scm.finance.domain.dto.FinancePayableSourceDto;
import com.xsy.scm.finance.domain.dto.FinancePayableSourceLineDto;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 应付生成器读取采购收货事实的只读 DAO。
 *
 * <p>
 * <b>刻意不继承 {@code BaseMapper}</b>：本接口没有采购实体写入方法，Mapper XML 只读取采购事实。
 *
 * <p>
 * 金额行从已确认的采购收货事实读取，不由调用方传入内存中的计算结果；收货确认与应付生成共用同一来源。
 */
@Mapper
public interface FinancePayableSourceDao {

    /**
     * 收货确认事实（单头维度）。
     *
     * <p>
     * 谓词 {@code status = 'CONFIRMED'} 保证未确认的收货单不会生成应付； 本方法只服务**正在被确认**的收货单 （同事务内 {@code confirm} 已把该行置为
     * CONFIRMED，PostgreSQL 能看见自身未提交的写入）。 返回 {@code null} 表示调用点用错了对象，必须失败而不是静默跳过。
     */
    FinancePayableSourceDto selectConfirmedReceipt(@Param("purchaseReceiptId") Long purchaseReceiptId);

    /**
     * 收货确认事实（明细维度），只含**有效量 &gt; 0** 的行，按收货行的录入顺序返回。
     *
     * <p>
     * 一行都没收到就不该进应付明细（{@code finance_payable_item.quantity} 的库级 CHECK 是 {@code > 0}）；少收未交部分不产生任何财务事实。
     */
    List<FinancePayableSourceLineDto> selectConfirmedReceiptLines(@Param("purchaseReceiptId") Long purchaseReceiptId);

    Long selectPurchaserId(@Param("purchaseReceiptId") Long purchaseReceiptId);
}
