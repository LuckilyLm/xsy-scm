package com.xsy.scm.payment.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xsy.scm.payment.domain.entity.RefundFundingAllocationEntity;
import java.math.BigDecimal;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface RefundFundingAllocationDao extends BaseMapper<RefundFundingAllocationEntity> {
    boolean existsByOrder(@Param("orderId") Long orderId);
    int insertIgnoreDuplicate(RefundFundingAllocationEntity row);

    List<RefundFundingAllocationEntity> lockByRefund(@Param("refundId") Long refundId);

    List<RefundFundingAllocationEntity> listByRefund(@Param("refundId") Long refundId);

    BigDecimal sumAllocatedBySource(@Param("sourceMethod") String sourceMethod, @Param("sourceId") Long sourceId);

    int markProcessing(@Param("id") Long id, @Param("operator") String operator);

    int markSucceeded(@Param("id") Long id, @Param("resultSourceId") Long resultSourceId,
            @Param("operator") String operator);

    int markFailed(@Param("id") Long id, @Param("failureCode") String failureCode,
            @Param("failureMessage") String failureMessage, @Param("operator") String operator);

    int resetFailed(@Param("id") Long id, @Param("operator") String operator);
}
