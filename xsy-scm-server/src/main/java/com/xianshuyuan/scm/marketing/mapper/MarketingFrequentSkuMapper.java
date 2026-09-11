package com.xianshuyuan.scm.marketing.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.xianshuyuan.scm.marketing.entity.MarketingFrequentSkuEntity;
import com.xianshuyuan.scm.marketing.row.FrequentSkuRow;
import com.xianshuyuan.scm.marketing.row.ReorderItemRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface MarketingFrequentSkuMapper extends BaseMapper<MarketingFrequentSkuEntity> {

    /**
     * 客户常用菜品，按购买次数倒序。分页由 MyBatis-Plus 拦截器处理。
     */
    List<FrequentSkuRow> selectFrequent(IPage<?> page, @Param("customerId") long customerId);

    /**
     * "再来一单"：取指定订单的明细快照。
     */
    List<ReorderItemRow> selectOrderItems(@Param("orderId") long orderId);

    /**
     * 按客户与商品定位常购记录，用于下单后累加购买次数。
     */
    MarketingFrequentSkuEntity selectByCustomerAndSku(@Param("customerId") long customerId,
                                                      @Param("skuId") long skuId);
}
