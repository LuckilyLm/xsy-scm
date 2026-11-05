package com.xsy.scm.base.module.support.reload.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xsy.scm.base.module.support.reload.domain.ReloadItemEntity;
import com.xsy.scm.base.module.support.reload.domain.ReloadItemVO;
import org.apache.ibatis.annotations.Mapper;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * t_reload_item 数据表dao
 *
 */
@Mapper
public interface ReloadItemDao extends BaseMapper<ReloadItemEntity> {

    List<ReloadItemVO> query();
}
