package com.xianshuyuan.scm.marketing.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.xianshuyuan.scm.marketing.dto.PromotionPageQuery;
import com.xianshuyuan.scm.marketing.entity.MarketingPromotionEntity;
import com.xianshuyuan.scm.marketing.row.PromotionRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.OffsetDateTime;
import java.util.List;

@Mapper
public interface MarketingPromotionMapper extends BaseMapper<MarketingPromotionEntity> {

    List<PromotionRow> selectPage(IPage<?> page, @Param("query") PromotionPageQuery query);

    /**
     * 取当前时间生效（已启用且在活动窗口内）的促销，按优先级倒序。
     */
    List<MarketingPromotionEntity> selectEffective(@Param("now") OffsetDateTime now);
}
