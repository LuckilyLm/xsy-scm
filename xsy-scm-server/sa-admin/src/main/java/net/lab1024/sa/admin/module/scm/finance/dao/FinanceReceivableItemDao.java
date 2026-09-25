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
}
