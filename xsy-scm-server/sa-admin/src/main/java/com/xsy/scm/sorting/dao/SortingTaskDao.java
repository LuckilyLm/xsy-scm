package com.xsy.scm.sorting.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import com.xsy.scm.sorting.domain.entity.SortingTaskEntity;

@Mapper
public interface SortingTaskDao
        extends
            BaseMapper<
                    SortingTaskEntity> {
}
