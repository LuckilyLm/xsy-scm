package com.xianshuyuan.scm.marketing.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.xianshuyuan.scm.marketing.dto.CouponPageQuery;
import com.xianshuyuan.scm.marketing.entity.MarketingCouponEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface MarketingCouponMapper extends BaseMapper<MarketingCouponEntity> {

    List<MarketingCouponEntity> selectPage(IPage<?> page, @Param("query") CouponPageQuery query);
}
