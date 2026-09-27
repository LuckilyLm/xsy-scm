package com.xsy.scm.delivery.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import com.xsy.scm.delivery.domain.entity.DeliveryRouteOrderEntity;

@Mapper
public interface DeliveryRouteOrderDao
        extends
            BaseMapper<
                    DeliveryRouteOrderEntity> {
}
