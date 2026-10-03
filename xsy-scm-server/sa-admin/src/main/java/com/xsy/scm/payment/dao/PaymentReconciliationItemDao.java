package com.xsy.scm.payment.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xsy.scm.payment.domain.entity.PaymentReconciliationItemEntity;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 对账差异明细：只追加、只按父单读。 */
@Mapper
public interface PaymentReconciliationItemDao extends BaseMapper<PaymentReconciliationItemEntity> {

    int insertItem(@Param("row") PaymentReconciliationItemEntity row);

    List<PaymentReconciliationItemEntity> listByReconciliation(@Param("reconciliationId") Long reconciliationId);
}
