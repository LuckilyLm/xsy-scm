package net.lab1024.sa.admin.module.scm.finance.dao;

import net.lab1024.sa.admin.module.scm.finance.domain.dto.FinanceReceivableSourceDto;
import net.lab1024.sa.admin.module.scm.finance.domain.dto.FinanceReceivableSourceLineDto;
import net.lab1024.sa.admin.module.scm.finance.domain.dto.FinanceReturnSourceDto;
import net.lab1024.sa.admin.module.scm.finance.domain.dto.FinanceReturnSourceLineDto;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 应收生成器读取的签收与出库事实（设计稿 §0 第 2 条允许的**只读跨域 DAO**，形态照
 * {@link FinancePayableSourceDao}）。
 *
 * <p><b>刻意不继承 {@code BaseMapper}</b>：本接口没有实体可写，越界写在这里是够不着的能力，
 * 不是靠纪律约束的口子。
 *
 * <p><b>为什么由财务侧回读，而不是让 {@code DeliveryRouteService.sign} 把 {@code signedAt} 传进来</b>：
 * {@code markSigned} 的 {@code signed_at} 写的是数据库时钟 {@code now()}，调用方手里没有这个值；
 * 传 {@code OffsetDateTime.now()} 会让 {@code receivable.event_at} 成为一个比签收时刻更晚的近似值，
 * 而「应收发生在何时」必须与「客户何时签收」是同一个事实（第一批 Q1）。
 */
@Mapper
public interface FinanceReceivableSourceDao {

    /**
     * 已签收分配行 + 订单的结算对方（单头维度）。
     *
     * <p>谓词 {@code fulfillment_status = 'SIGNED'} 是「{@code EXCEPTION} 不形成应收」（第二批 Q6）
     * 的库级表达：异常签收走同一条 {@code markSigned}，状态不是 SIGNED 时本方法返回 {@code null}，
     * 调用方必须失败而不是静默跳过。
     */
    FinanceReceivableSourceDto selectSignedAssignment(
            @Param("deliveryRouteOrderId") Long deliveryRouteOrderId);

    /**
     * 该订单的实际出库行（明细维度），按出库行主键升序。
     *
     * <p>{@code sales_order_item_id IS NOT NULL} 排除手工出库（第二批 Q5：手工出库不产生应收）；
     * 结果为空即「签收成功但零实发」，属第二批 Q8 的成功跳过，不是异常。
     */
    List<FinanceReceivableSourceLineDto> selectOutboundLines(
            @Param("salesOrderId") Long salesOrderId);

    /**
     * 已批准退货事实（单头维度）。谓词 {@code status = 'APPROVED'} 是「退货批准才是红字来源」
     * （第二批 Q27）的库级表达；{@code PENDING / REJECTED / CANCELLED} 一律读不到，
     * 因此调用点传错 id 时会失败而不是静默生成。
     */
    FinanceReturnSourceDto selectApprovedReturn(@Param("orderReturnId") Long orderReturnId);

    /**
     * 该订单**全部已批准退货**的 id，按 id 升序（= 批准发生顺序）。
     *
     * <p>签收补生成用它遍历（第二批 Q27 的「先退后签」③）：升序保证多张退货的补生成顺序
     * 与并发到达顺序无关，红字之间互不依赖，因此顺序只影响日志可读性。
     */
    List<Long> selectApprovedReturnIds(@Param("salesOrderId") Long salesOrderId);

    /**
     * 已批准退货的**有效红字行**：只取 {@code approved_quantity > 0} 且
     * {@code approved_amount > 0} 的行（设计稿 §8.2 第一条：逐行跳过非正金额）。
     *
     * <p>不加 {@code approved_quantity IS NOT NULL} 之类的分支：{@code > 0} 对 NULL 恒不成立，
     * 未批准的行自然被排除。
     */
    List<FinanceReturnSourceLineDto> selectApprovedReturnLines(
            @Param("orderReturnId") Long orderReturnId);
}
