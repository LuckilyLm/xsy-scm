package com.xsy.scm.pricing.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.OffsetDateTime;
import java.util.List;

import com.xsy.scm.pricing.domain.entity.AgreementPriceEntity;
import com.xsy.scm.pricing.domain.form.AgreementPriceQueryForm;
import com.xsy.scm.pricing.domain.vo.AgreementPriceVO;

@Mapper
public interface AgreementPriceDao extends BaseMapper<AgreementPriceEntity> {
    List<AgreementPriceVO> queryPage(Page<?> page, @Param("query") AgreementPriceQueryForm query);

    AgreementPriceVO detail(@Param("id") Long agreementPriceId);

    Long lockParent(@Param("id") Long customerId);

    long countOverlapping(@Param("dimension") Long customerId, @Param("skuId") Long skuId,
            @Param("from") OffsetDateTime effectiveFrom, @Param("to") OffsetDateTime effectiveTo,
            @Param("excludeId") Long excludedAgreementPriceId);

    List<AgreementPriceEntity> selectEffective(@Param("dimension") Long customerId, @Param("skuIds") List<Long> skuIds,
            @Param("at") OffsetDateTime priceAt);

    int softDelete(@Param("id") Long agreementPriceId, @Param("version") Integer version,
            @Param("operator") String operator);

    void log(@Param("id") Long agreementPriceId, @Param("operation") String operationType,
            @Param("operator") String operator, @Param("before") String beforeSnapshot,
            @Param("after") String afterSnapshot);
}
