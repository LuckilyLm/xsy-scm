package com.xianshuyuan.scm.marketing.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xianshuyuan.scm.marketing.entity.MarketingThemeConfigEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface MarketingThemeConfigMapper extends BaseMapper<MarketingThemeConfigEntity> {

    MarketingThemeConfigEntity selectCurrent();
}
