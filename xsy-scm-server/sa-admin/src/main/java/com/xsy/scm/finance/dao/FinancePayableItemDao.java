package com.xsy.scm.finance.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xsy.scm.finance.domain.entity.FinancePayableItemEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 应付明细读写，append-only 契约同 {@link FinanceReceivableDao}。
 */
@Mapper
public interface FinancePayableItemDao extends BaseMapper<FinancePayableItemEntity> {

    /**
     * 一条有效收货明细只能生成一条应付明细，数据库来源唯一索引负责防重。重复写入需要区分合法重放与不一致业务记录。
     */
    int insertOnConflictDoNothing(FinancePayableItemEntity entity);

    FinancePayableItemEntity selectOriginalItem(@Param("payableId") Long payableId,
        @Param("purchaseOrderItemId") Long purchaseOrderItemId);

    List<FinancePayableItemEntity> selectByPayableId(@Param("payableId") Long payableId);
}
