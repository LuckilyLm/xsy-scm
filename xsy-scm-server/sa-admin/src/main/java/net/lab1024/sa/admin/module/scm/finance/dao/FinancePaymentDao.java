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

    /**
     * 付款单号序列（全局非重置，不按日归零）。必须在事务内调用：
     * {@code nextval} 不随事务回滚，跳号是可接受的代价（与应付 / 收款单号同一条纪律）。
     */
    long nextPaymentNo();

    /**
     * 插入一笔正常付款，同一 {@code ORDER_REFUND} 已有付款时什么都不做。
     *
     * <p>冲突目标与 {@code uk_finance_payment_source_active} 的列和谓词**逐字一致**（Q11 纪律）：
     * 少写谓词会命中「无索引可仲裁」而直接报错，用无目标的 {@code ON CONFLICT DO NOTHING}
     * 会把 {@code payment_no} 撞号一起吞掉。
     *
     * <p>供应商付款的 {@code source_id} 为 NULL，落在谓词之外，因此本方法的 0 行返回值
     * <b>只可能</b>意味着「这张退款已经付过了」，服务层据此给 41139 而不是泄漏约束名。
     * 这也是并发双付款的最终仲裁点：后到者在此等待前者提交后重新检查谓词，得到 0。
     *
     * @return 1 = 本次登记成功（{@code id} 已回填）；0 = 该退款已有付款
     */
    int insertNormalOnConflictDoNothing(FinancePaymentEntity entity);
}
