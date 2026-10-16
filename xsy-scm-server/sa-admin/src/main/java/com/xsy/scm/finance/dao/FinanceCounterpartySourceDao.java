package com.xsy.scm.finance.dao;

import com.xsy.scm.finance.domain.dto.FinanceCustomerFactDto;
import com.xsy.scm.finance.domain.dto.FinanceSupplierFactDto;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 财务通过只读 DAO 查询客户或供应商资料，不能修改业务主档。客户归属权限必须复用统一的员工范围规则。 */
@Mapper
public interface FinanceCounterpartySourceDao {

    /**
     * 未删除的客户行（名称快照 + 归属业务员）。
     *
     * <p>
     * 只判<b>存在性与 deleted</b>；当前收付款命令不以客户经营状态为前置条件。
     */
    FinanceCustomerFactDto selectCustomer(@Param("customerId") Long customerId);

    /**
     * 未删除的供应商行（只到名称为止）。
     *
     * <p>
     * 供应商主档没有 owner 列，按采购团队共享读取，因此本方法不带范围字段也不做范围判定。 {@code status} 不作为付款前置条件，停用供应商仍可能需要结清历史债务。
     */
    FinanceSupplierFactDto selectSupplier(@Param("supplierId") Long supplierId);
}
