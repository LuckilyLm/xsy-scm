package com.xsy.scm.finance.dao;

import com.xsy.scm.finance.domain.dto.FinancePayableSourceDto;
import com.xsy.scm.finance.domain.dto.FinancePayableSourceLineDto;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 应付生成器读取采购收货数据的只读 DAO。
 *
 * <p>
 * 刻意不继承 {@code BaseMapper}：这里没有采购实体的写入方法，Mapper XML 只读采购数据。
 *
 * <p>
 * 金额行从已确认的收货记录读取，不由调用方传入内存中算好的结果，收货确认与应付生成共用同一来源。
 */
@Mapper
public interface FinancePayableSourceDao {
    FinancePayableSourceDto selectPurchaseAccount(@Param("id") Long id);
    List<FinancePayableSourceLineDto> selectPurchaseAccountLines(@Param("id") Long id);
    Long selectPurchaserByOrderId(@Param("purchaseOrderId") Long purchaseOrderId);

    /**
     * 收货确认数据（单头维度）。
     *
     * <p>
     * 条件 {@code status = 'CONFIRMED'} 保证未确认的收货单不会生成应付；本方法只服务正在被确认的收货单（同一事务内 {@code confirm} 已把该行置为
     * CONFIRMED，能看到自身未提交的写入）。返回 {@code null} 表示调用点用错了对象，必须失败而不是跳过。
     */
    FinancePayableSourceDto selectConfirmedReceipt(@Param("purchaseReceiptId") Long purchaseReceiptId);

    /**
     * 收货确认数据（明细维度），只含有效量 &gt; 0 的行，按收货行录入顺序返回。
     *
     * <p>
     * 没收到货就不该进应付明细（{@code xsy_finance_payable_item.quantity} 的数据库 CHECK 要求 {@code > 0}）；少收未交的部分不产生财务记录。
     */
    List<FinancePayableSourceLineDto> selectConfirmedReceiptLines(@Param("purchaseReceiptId") Long purchaseReceiptId);

    Long selectPurchaserId(@Param("purchaseReceiptId") Long purchaseReceiptId);
}
