package net.lab1024.sa.admin.module.scm.delivery.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import net.lab1024.sa.admin.module.scm.delivery.domain.entity.DeliveryDriverEntity;

@Mapper
public interface DeliveryDriverDao extends BaseMapper<DeliveryDriverEntity> {
}
