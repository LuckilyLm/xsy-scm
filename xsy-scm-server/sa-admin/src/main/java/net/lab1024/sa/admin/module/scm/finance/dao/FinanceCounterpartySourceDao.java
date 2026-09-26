package net.lab1024.sa.admin.module.scm.finance.dao;

import net.lab1024.sa.admin.module.scm.finance.domain.dto.FinanceCustomerFactDto;
import net.lab1024.sa.admin.module.scm.finance.domain.dto.FinanceSupplierFactDto;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 财务域读取的**对方主档事实**（只读跨域 DAO，形态照 {@link FinancePayableSourceDao}）。
 *
 * <p>刻意不继承 {@code BaseMapper}：财务域对业务表没有写入口。
 * {@code FinanceReadOnlyContractTest} 另外扫描本包 mapper XML，防的是有人写下一句 DML。
 *
 * <p><b>客户事实只在这里读一次</b>：收款登记与退款付款的 CUSTOMER 侧共用同一条
 * {@code customer_id → seller_id → customerSellerScope} 判定（第三批 D-5），
 * 复制到第二个 DAO 就等于让两处的范围口径可以各自漂移。
 */
@Mapper
public interface FinanceCounterpartySourceDao {

    /**
     * 未删除的客户行（名称快照 + 归属业务员）。
     *
     * <p>只判**存在性与 deleted**：没有「客户必须处于某个经营状态才可收 / 付款」的裁决，
     * 自行加一条等于发明新的业务规则（{@code customer.status} 有四值，含 BLACKLIST / SUSPENDED，
     * 是否禁止对它们收付款属未决问题，需要负责人裁决）。
     */
    FinanceCustomerFactDto selectCustomer(@Param("customerId") Long customerId);

    /**
     * 未删除的供应商行（只到名称为止）。
     *
     * <p>D-5 与 P0 裁决 7：供应商主档无 owner 列、按采购团队共享读取，因此本方法
     * 既不带范围字段也不做范围判定。{@code status} 刻意<b>不作为付款前置条件</b> ——
     * 「已停用供应商能否结清历史债务」没有被裁决禁止，擅自加 {@code ENABLED} 门槛会挡住合法清偿。
     */
    FinanceSupplierFactDto selectSupplier(@Param("supplierId") Long supplierId);
}
