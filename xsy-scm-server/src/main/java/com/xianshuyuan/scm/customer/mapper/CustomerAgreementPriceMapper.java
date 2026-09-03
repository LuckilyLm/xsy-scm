package com.xianshuyuan.scm.customer.mapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper; import com.xianshuyuan.scm.customer.entity.CustomerAgreementPriceEntity; import org.apache.ibatis.annotations.*; import java.time.OffsetDateTime; import java.util.List;
@Mapper public interface CustomerAgreementPriceMapper extends BaseMapper<CustomerAgreementPriceEntity> {
 List<CustomerAgreementPriceEntity> selectEffective(@Param("customerId") long customerId,@Param("skuIds") List<Long> skuIds,@Param("at") OffsetDateTime at);
 int countOverlapping(@Param("id") Long id,@Param("customerId") long customerId,@Param("skuId") long skuId,@Param("from") OffsetDateTime from,@Param("to") OffsetDateTime to);
 List<CustomerAgreementPriceEntity> selectActive();
}
