package com.xsy.scm.base.module.support.helpdoc.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xsy.scm.base.module.support.helpdoc.domain.entity.HelpDocCatalogEntity;
import org.apache.ibatis.annotations.Mapper;
import org.springframework.stereotype.Component;

/**
 * 帮助文档目录
 *
 */
@Mapper
public interface HelpDocCatalogDao extends BaseMapper<HelpDocCatalogEntity> {

}
