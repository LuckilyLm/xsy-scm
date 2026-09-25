package net.lab1024.sa.admin.module.scm.finance.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import net.lab1024.sa.admin.module.scm.finance.domain.entity.FinanceReceivableItemEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 应收明细读写，append-only 契约同 {@link FinanceReceivableDao}。
 *
 * <p>行级防重锚点是 {@code (source_type, source_id)} 上的
 * {@code uk_finance_receivable_item_source_active}：正常明细锚定出库行主键，
 * 红字明细锚定退货行主键。
 */
@Mapper
public interface FinanceReceivableItemDao extends BaseMapper<FinanceReceivableItemEntity> {

    /**
     * 插入应收明细，来源出库行已入账时什么都不做。
     *
     * <p>冲突目标与 {@code uk_finance_receivable_item_source_active} 逐字一致（Q11 纪律）。
     * 与单头不同：调用方只在「单头刚刚由本次调用插入」之后才写明细，此时返回 0
     * 不可能是重放，而是同一条出库行被挂到了两张应收单上 —— 属于数据异常，必须失败。
     */
    int insertOnConflictDoNothing(FinanceReceivableItemEntity entity);
}
