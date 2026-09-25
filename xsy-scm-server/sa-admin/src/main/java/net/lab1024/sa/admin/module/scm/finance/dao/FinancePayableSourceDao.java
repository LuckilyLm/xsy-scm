package net.lab1024.sa.admin.module.scm.finance.dao;

import net.lab1024.sa.admin.module.scm.finance.domain.dto.FinancePayableSourceDto;
import net.lab1024.sa.admin.module.scm.finance.domain.dto.FinancePayableSourceLineDto;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 应付生成器读取的采购收货事实（设计稿 §0 第 2 条允许的**只读跨域 DAO**）。
 *
 * <p><b>刻意不继承 {@code BaseMapper}</b>：本接口没有任何实体可写，也没有任何写方法，
 * 越界写在这里不是纪律而是够不着的能力。{@code FinanceReadOnlyContractTest} 另外钉一条
 * 静态扫描，防的是有人在 mapper XML 里加一句写采购表的 SQL。
 *
 * <p><b>为什么由财务侧重新查一遍，而不是让 {@code PurchaseReceiptService} 把内存里的行传进来</b>：
 * 金额口径只能有一个来源。收货确认有两条触发路径（{@code DIRECT} / {@code WAREHOUSE_CONFIRM}），
 * 未来还会有重试与补生成，按调用方装配就要求每条路径各自算一边金额；取数收进财务域、
 * 防重交给来源唯一索引，才是单一口径（设计稿 §0 第 2 条的理由）。
 */
@Mapper
public interface FinancePayableSourceDao {

    /**
     * 收货确认事实（单头维度）。
     *
     * <p>谓词 {@code status = 'CONFIRMED'} 是「未确认的收货单不产生应付」（第一批 Q9）的库级表达，
     * 不是锦上添花的校验：D-1 不回填，因此本方法只能为**正在被确认**的那张单服务
     * （同事务内 {@code confirm} 已把该行置为 CONFIRMED，PostgreSQL 能看见自身未提交的写入）。
     * 返回 {@code null} 表示调用点用错了对象，必须失败而不是静默跳过。
     */
    FinancePayableSourceDto selectConfirmedReceipt(@Param("purchaseReceiptId") Long purchaseReceiptId);

    /**
     * 收货确认事实（明细维度），只含**有效量 &gt; 0** 的行，按收货行的录入顺序返回。
     *
     * <p>一行都没收到就不该进应付明细（{@code finance_payable_item.quantity} 的库级 CHECK 是
     * {@code > 0}）；少收未交部分不产生任何财务事实（第二批 Q12）。
     */
    List<FinancePayableSourceLineDto> selectConfirmedReceiptLines(
            @Param("purchaseReceiptId") Long purchaseReceiptId);
}
