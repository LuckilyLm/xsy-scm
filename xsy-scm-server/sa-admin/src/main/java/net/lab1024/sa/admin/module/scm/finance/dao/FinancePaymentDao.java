package net.lab1024.sa.admin.module.scm.finance.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import net.lab1024.sa.admin.module.scm.finance.domain.entity.FinancePaymentEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 付款读写，append-only 契约同 {@link FinanceReceiptDao}。
 *
 * <p>退款付款的行级防重锚点是 {@code uk_finance_payment_source_active}
 * （{@code (source_type, source_id) WHERE deleted = FALSE AND source_id IS NOT NULL}）：
 * 同一张 {@code order_refund} 最多一笔正式退款付款（Q19 / Q26）。
 * 反向付款的 {@code source_id} 必须为 NULL，因此不与原行抢这个键（D-3）。
 */
@Mapper
public interface FinancePaymentDao extends BaseMapper<FinancePaymentEntity> {
}
