package com.xianshuyuan.scm.customer.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.xianshuyuan.scm.customer.dto.CustomerTypePricePageQuery;
import com.xianshuyuan.scm.customer.entity.CustomerTypePriceEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.OffsetDateTime;
import java.util.List;

@Mapper
public interface CustomerTypePriceMapper extends BaseMapper<CustomerTypePriceEntity> {
    List<CustomerTypePriceEntity> selectEffective(@Param("customerTypeId") long customerTypeId,
                                                   @Param("skuIds") List<Long> skuIds,
                                                   @Param("at") OffsetDateTime at);

    int countOverlapping(@Param("id") Long id, @Param("customerTypeId") long customerTypeId,
                         @Param("skuId") long skuId, @Param("from") OffsetDateTime from,
                         @Param("to") OffsetDateTime to);

    IPage<CustomerTypePriceEntity> selectPricePage(IPage<CustomerTypePriceEntity> page,
                                                   @Param("query") CustomerTypePricePageQuery query);

    void lockCustomerType(@Param("customerTypeId") long customerTypeId);

    int softDelete(@Param("id") long id, @Param("version") int version);
}
