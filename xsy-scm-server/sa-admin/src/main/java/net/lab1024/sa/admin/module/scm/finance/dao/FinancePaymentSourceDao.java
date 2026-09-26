package net.lab1024.sa.admin.module.scm.finance.dao;

import net.lab1024.sa.admin.module.scm.finance.domain.dto.FinanceRefundFactDto;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 付款登记读取的**来源事实**（只读跨域 DAO，形态照 {@link FinancePayableSourceDao}）。
 *
 * <p>刻意不继承 {@code BaseMapper}：财务域对业务表没有写入口；
 * 对方主档（客户 / 供应商）走 {@link FinanceCounterpartySourceDao}，本 DAO 只管来源单据。
 */
@Mapper
public interface FinancePaymentSourceDao {

    /**
     * 未删除的退款行（状态 + 客户 + 应退金额），供 Q19 的三条校验使用。
     *
     * <p>读侧不带 {@code status} 过滤：{@code PENDING} 必须能读出来，服务层才能给出
     * 可解释的 41139，而不是把它伪装成「退款不存在」。
     */
    FinanceRefundFactDto selectOrderRefund(@Param("refundId") Long refundId);
}
