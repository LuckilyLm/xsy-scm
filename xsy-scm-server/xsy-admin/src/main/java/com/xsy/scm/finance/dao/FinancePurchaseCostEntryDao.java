package com.xsy.scm.finance.dao;

import java.util.List;
import com.xsy.scm.finance.domain.dto.FinancePurchaseCostTargetDto;
import com.xsy.scm.finance.domain.entity.FinancePurchaseCostEntryEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface FinancePurchaseCostEntryDao {
    FinancePurchaseCostTargetDto capacity(@Param("payableId") Long payableId, @Param("orderItemId") Long orderItemId);
    int insert(FinancePurchaseCostEntryEntity entry);
    List<FinancePurchaseCostTargetDto> lockTargets(@Param("receiptItemId") Long receiptItemId,
        @Param("receiptId") Long receiptId, @Param("orderItemId") Long orderItemId,
        @Param("accountItemId") Long accountItemId, @Param("accountId") Long accountId);
}
