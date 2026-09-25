package net.lab1024.sa.admin.module.scm.finance.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import net.lab1024.sa.admin.module.scm.finance.domain.entity.FinancePayableItemEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 应付明细读写，append-only 契约同 {@link FinanceReceivableDao}。
 */
@Mapper
public interface FinancePayableItemDao extends BaseMapper<FinancePayableItemEntity> {
}
