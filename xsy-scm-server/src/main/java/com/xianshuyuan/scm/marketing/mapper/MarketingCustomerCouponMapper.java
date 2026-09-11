package com.xianshuyuan.scm.marketing.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xianshuyuan.scm.marketing.entity.MarketingCustomerCouponEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface MarketingCustomerCouponMapper extends BaseMapper<MarketingCustomerCouponEntity> {

    List<MarketingCustomerCouponEntity> selectByCustomer(@Param("customerId") long customerId,
                                                         @Param("status") String status);

    MarketingCustomerCouponEntity selectByCouponNo(@Param("couponNo") String couponNo);

    /**
     * 统计客户已领取某模板的券数量，用于每人限领校验。
     */
    int countIssued(@Param("couponId") long couponId, @Param("customerId") long customerId);
}
