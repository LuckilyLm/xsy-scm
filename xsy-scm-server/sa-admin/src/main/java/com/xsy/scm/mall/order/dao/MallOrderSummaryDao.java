package com.xsy.scm.mall.order.dao;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xsy.scm.mall.order.domain.vo.MallOrderSummaryVO;

import java.time.OffsetDateTime;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface MallOrderSummaryDao {

    /**
     * 按商品规格聚合的分页行。
     *
     * <p>
     * 传 {@link Page} 只为拿到 LIMIT / OFFSET，总数由 {@link #summaryTotals} 给出：分组查询的 count 会被 MyBatis-Plus
     * 改写成对整表计数，与「有多少个规格」不是一回事。
     */
    List<MallOrderSummaryVO.Item> summaryPage(Page<?> page, @Param("customerId") Long customerId,
        @Param("startAt") OffsetDateTime startAt, @Param("endAt") OffsetDateTime endAt);

    /**
     * 区间合计，统计方式与分页行完全一致。
     */
    MallOrderSummaryVO.Totals summaryTotals(@Param("customerId") Long customerId,
        @Param("startAt") OffsetDateTime startAt, @Param("endAt") OffsetDateTime endAt);
}
