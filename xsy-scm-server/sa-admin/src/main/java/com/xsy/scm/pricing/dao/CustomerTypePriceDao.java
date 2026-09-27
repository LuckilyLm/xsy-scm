package com.xsy.scm.pricing.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.OffsetDateTime;
import java.util.List;

import com.xsy.scm.pricing.domain.entity.CustomerTypePriceEntity;
import com.xsy.scm.pricing.domain.form.CustomerTypePriceQueryForm;
import com.xsy.scm.pricing.domain.vo.CustomerTypePriceVO;

@Mapper
public interface CustomerTypePriceDao
        extends
            BaseMapper<
                    CustomerTypePriceEntity> {
    List<
            CustomerTypePriceVO> queryPage(
                    Page<
                            ?> page,
                    @Param("query") CustomerTypePriceQueryForm query);

    CustomerTypePriceVO detail(@Param("id") Long customerTypePriceId);

    Long lockParent(@Param("id") Long customerTypeId);

    long countOverlapping(@Param("dimension") Long customerTypeId, @Param("skuId") Long skuId,
            @Param("from") OffsetDateTime effectiveFrom, @Param("to") OffsetDateTime effectiveTo,
            @Param("excludeId") Long excludedCustomerTypePriceId);

    List<
            CustomerTypePriceEntity> selectEffective(@Param("dimension") Long customerTypeId,
                    @Param("skuIds") List<
                            Long> skuIds,
                    @Param("at") OffsetDateTime priceAt);

    int softDelete(@Param("id") Long customerTypePriceId, @Param("version") Integer version,
            @Param("operator") String operator);

    void log(@Param("id") Long customerTypePriceId, @Param("operation") String operationType,
            @Param("operator") String operator, @Param("before") String beforeSnapshot,
            @Param("after") String afterSnapshot);
}
