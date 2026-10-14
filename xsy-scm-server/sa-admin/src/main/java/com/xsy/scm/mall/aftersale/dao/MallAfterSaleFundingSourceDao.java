package com.xsy.scm.mall.aftersale.dao;

import com.xsy.scm.mall.aftersale.domain.vo.MallAfterSaleFundingTimeVO;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 商城只读投影：只返回已成立的退款资金事实及其发生时刻。 */
@Mapper
public interface MallAfterSaleFundingSourceDao {
    List<MallAfterSaleFundingTimeVO> completedRefunds(@Param("refundIds") List<Long> refundIds);

    List<MallAfterSaleFundingTimeVO> allocationSuccessTimes(@Param("refundId") Long refundId);
}
