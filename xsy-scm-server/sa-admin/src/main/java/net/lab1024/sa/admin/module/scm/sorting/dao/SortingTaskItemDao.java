package net.lab1024.sa.admin.module.scm.sorting.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import net.lab1024.sa.admin.module.scm.sorting.domain.entity.SortingTaskItemEntity;

@Mapper
public interface SortingTaskItemDao extends BaseMapper<SortingTaskItemEntity> {
}
