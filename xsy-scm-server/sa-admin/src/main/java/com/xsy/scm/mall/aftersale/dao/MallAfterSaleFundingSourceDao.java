package com.xsy.scm.mall.aftersale.dao;

import com.xsy.scm.mall.aftersale.domain.vo.MallAfterSaleFundingTimeVO;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 商城只读查询：只返回已完成的退款记录及其发生时间。
 */
@Mapper
public interface MallAfterSaleFundingSourceDao {
    List<MallAfterSaleFundingTimeVO> completedRefunds(@Param("refundIds") List<Long> refundIds);

    List<MallAfterSaleFundingTimeVO> allocationSuccessTimes(@Param("refundId") Long refundId);
}
