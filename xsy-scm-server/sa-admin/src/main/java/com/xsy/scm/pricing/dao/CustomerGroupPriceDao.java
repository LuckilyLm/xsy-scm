package com.xsy.scm.pricing.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.OffsetDateTime;
import java.util.List;

import com.xsy.scm.pricing.domain.entity.CustomerGroupPriceEntity;
import com.xsy.scm.pricing.domain.form.CustomerGroupPriceQueryForm;
import com.xsy.scm.pricing.domain.vo.CustomerGroupPriceVO;

@Mapper
public interface CustomerGroupPriceDao extends BaseMapper<CustomerGroupPriceEntity> {
    List<CustomerGroupPriceVO> queryPage(Page<?> page, @Param("query") CustomerGroupPriceQueryForm query);

    CustomerGroupPriceVO detail(@Param("id") Long groupPriceId);

    /** 锁分组行：同一分组内的有效期重叠检查与写入串行化。 */
    Long lockGroup(@Param("groupId") Long groupId);

    long countOverlapping(@Param("groupId") Long groupId, @Param("spuId") Long spuId,
        @Param("saleUnit") String saleUnit, @Param("from") OffsetDateTime effectiveFrom,
        @Param("to") OffsetDateTime effectiveTo, @Param("excludeId") Long excludedGroupPriceId);

    /** 只返回启用分组的有效价；停用分组不参与取价。 */
    List<CustomerGroupPriceEntity> selectEffective(@Param("groupId") Long groupId, @Param("spuIds") List<Long> spuIds,
        @Param("at") OffsetDateTime priceAt);

    int softDelete(@Param("id") Long groupPriceId, @Param("version") Integer version,
        @Param("operator") String operator);

    void log(@Param("id") Long groupPriceId, @Param("operation") String operationType,
        @Param("operator") String operator, @Param("before") String beforeSnapshot,
        @Param("after") String afterSnapshot);
}
