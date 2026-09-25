package net.lab1024.sa.admin.module.scm.finance.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import net.lab1024.sa.admin.module.scm.finance.domain.entity.FinancePayableItemEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 应付明细读写，append-only 契约同 {@link FinanceReceivableDao}。
 */
@Mapper
public interface FinancePayableItemDao extends BaseMapper<FinancePayableItemEntity> {

    /**
     * 插入应付明细，来源身份已存在时什么都不做。
     *
     * <p>冲突目标与 {@code uk_finance_payable_item_source_active} 逐字一致（Q11 纪律）。
     * 与单头不同：调用方只在「单头刚刚由本次调用插入」之后才写明细，此时返回 0
     * 不可能是重放，而是这一条收货行已经挂在别的应付单上 —— 属于数据异常，必须失败。
     */
    int insertOnConflictDoNothing(FinancePayableItemEntity entity);
}
