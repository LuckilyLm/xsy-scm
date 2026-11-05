package com.xsy.scm.customer.dao;

import com.xsy.scm.customer.domain.vo.CustomerFrequentSkuVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 客户常购商品从销售订单只读汇总，不复制一套客户购买明细，也不修改订单。
 */
@Mapper
public interface CustomerFrequentSkuDao {

    /**
     * {@code since}（含）之后已确认的订单，按 (SKU, 销售单位) 聚合出常购行。
     *
     * @param customerId
     *            客户 ID，聚合的唯一范围
     * @param since
     *            时间下界（Asia/Shanghai 日界算出的时刻），过滤 {@code confirmed_at}
     * @param limit
     *            返回分组数上限（Service 已裁剪到安全区间）
     */
    List<CustomerFrequentSkuVO> frequentSkus(@Param("customerId") Long customerId, @Param("since") OffsetDateTime since,
        @Param("limit") int limit);
}
