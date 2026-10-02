package com.xsy.scm.finance.dao;

import com.xsy.scm.common.scope.ScmValueScope;
import com.xsy.scm.finance.domain.dto.FinanceCreditExposureDto;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface CustomerCreditDao {
    FinanceCreditExposureDto selectExposure(@Param("settlementCustomerId") Long settlementCustomerId,
            @Param("scope") ScmValueScope scope);
}
