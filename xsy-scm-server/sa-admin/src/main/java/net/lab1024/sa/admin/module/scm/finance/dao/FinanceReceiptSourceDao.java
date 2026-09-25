package net.lab1024.sa.admin.module.scm.finance.dao;

import net.lab1024.sa.admin.module.scm.finance.domain.dto.FinanceCustomerFactDto;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 收款登记读取的客户事实（只读跨域 DAO，形态照 {@link FinancePayableSourceDao}）。
 *
 * <p>刻意不继承 {@code BaseMapper}：财务域对业务表没有写入口。
 * {@code FinanceReadOnlyContractTest} 另外扫描本包 mapper XML，防的是有人写下一句 DML。
 */
@Mapper
public interface FinanceReceiptSourceDao {

    /**
     * 未删除的客户行（名称快照 + 归属业务员）。
     *
     * <p>只判**存在性与 deleted**：本阶段没有「客户必须处于某个经营状态才可被收款」的裁决，
     * 自行加一条等于发明新的业务规则（{@code customer.status} 有四值，含 BLACKLIST / SUSPENDED，
     * 是否禁止对它们登记收款属未决问题，需要负责人裁决）。
     */
    FinanceCustomerFactDto selectCustomer(@Param("customerId") Long customerId);
}
