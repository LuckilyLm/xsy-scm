package com.xsy.scm.finance.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xsy.scm.finance.domain.entity.FinanceReceivableItemEntity;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 应收明细读写，只能追加，规则同 {@link FinanceReceivableDao}。
 *
 * <p>
 * 行级防重依据是 {@code (source_type, source_id)} 上的 {@code uk_finance_receivable_item_source_active}：正常明细锚定出库行主键，
 * 红字明细锚定退货行主键。
 */
@Mapper
public interface FinanceReceivableItemDao extends BaseMapper<FinanceReceivableItemEntity> {

    /**
     * 同一出库行只能生成一次应收明细。出现来源唯一冲突要检查是否重复入账，不得把异常冲突当成功。
     */
    int insertOnConflictDoNothing(FinanceReceivableItemEntity entity);

    List<FinanceReceivableItemEntity> selectByReceivableId(
        @org.apache.ibatis.annotations.Param("receivableId") Long receivableId);
}
