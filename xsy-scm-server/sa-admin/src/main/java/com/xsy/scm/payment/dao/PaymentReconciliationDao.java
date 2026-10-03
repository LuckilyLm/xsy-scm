package com.xsy.scm.payment.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xsy.scm.payment.domain.entity.PaymentReconciliationEntity;
import com.xsy.scm.payment.domain.form.PaymentReconciliationQueryForm;
import java.time.LocalDate;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface PaymentReconciliationDao extends BaseMapper<PaymentReconciliationEntity> {

    List<PaymentReconciliationEntity> queryPage(Page<?> page,
            @Param("query") PaymentReconciliationQueryForm query);

    PaymentReconciliationEntity selectByProviderAndDate(@Param("provider") String provider,
            @Param("bizDate") LocalDate bizDate);

    PaymentReconciliationEntity lockById(@Param("id") Long id);
}
