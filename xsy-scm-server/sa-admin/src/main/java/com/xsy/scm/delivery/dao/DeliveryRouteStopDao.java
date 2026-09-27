package com.xsy.scm.delivery.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import com.xsy.scm.delivery.domain.entity.DeliveryRouteStopEntity;

@Mapper
public interface DeliveryRouteStopDao
        extends
            BaseMapper<
                    DeliveryRouteStopEntity> {
}
