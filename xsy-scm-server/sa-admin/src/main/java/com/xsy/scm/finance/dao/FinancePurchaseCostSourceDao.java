package com.xsy.scm.finance.dao;

import com.xsy.scm.finance.domain.dto.FinancePurchaseCostSourceDto;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface FinancePurchaseCostSourceDao {
    FinancePurchaseCostSourceDto find(@Param("adjustmentId") Long adjustmentId);
}
