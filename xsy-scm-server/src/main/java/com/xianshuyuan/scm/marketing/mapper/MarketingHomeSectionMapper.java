package com.xianshuyuan.scm.marketing.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xianshuyuan.scm.marketing.entity.MarketingHomeSectionEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.OffsetDateTime;
import java.util.List;

@Mapper
public interface MarketingHomeSectionMapper extends BaseMapper<MarketingHomeSectionEntity> {

    /**
     * 取当前时间启用且在上下线窗口内的首页板块，按 sort_order 升序。
     */
    List<MarketingHomeSectionEntity> selectActive(@Param("now") OffsetDateTime now);
}
