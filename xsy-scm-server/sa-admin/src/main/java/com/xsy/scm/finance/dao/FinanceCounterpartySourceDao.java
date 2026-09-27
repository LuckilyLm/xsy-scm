package com.xsy.scm.finance.dao;

import com.xsy.scm.finance.domain.dto.FinanceCustomerFactDto;
import com.xsy.scm.finance.domain.dto.FinanceSupplierFactDto;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 财务域读取的**对方主档事实**（只读跨域 DAO，形态照 {@link FinancePayableSourceDao}）。
 *
 * <p>刻意不继承 {@code BaseMapper}：财务域对业务表没有写入口，Mapper XML 只读取这些主档事实。
 *
 * <p><b>客户事实只在这里读一次</b>：收款登记与退款付款的 CUSTOMER 侧共用同一条
 * {@code customer_id → seller_id → customerSellerScope} 判定，
 * 复制到第二个 DAO 就等于让两处的范围口径可以各自漂移。
 */
@Mapper
public interface FinanceCounterpartySourceDao {

    /**
     * 未删除的客户行（名称快照 + 归属业务员）。
     *
     * <p>只判**存在性与 deleted**；当前收付款命令不以客户经营状态为前置条件。
     */
    FinanceCustomerFactDto selectCustomer(@Param("customerId") Long customerId);

    /**
     * 未删除的供应商行（只到名称为止）。
     *
     * <p>供应商主档没有 owner 列，按采购团队共享读取，因此本方法不带范围字段也不做范围判定。
     * {@code status} 不作为付款前置条件，停用供应商仍可能需要结清历史债务。
     */
    FinanceSupplierFactDto selectSupplier(@Param("supplierId") Long supplierId);
}
