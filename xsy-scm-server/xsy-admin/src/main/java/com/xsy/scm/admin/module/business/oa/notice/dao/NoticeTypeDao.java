package com.xsy.scm.admin.module.business.oa.notice.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xsy.scm.admin.module.business.oa.notice.domain.entity.NoticeTypeEntity;
import org.apache.ibatis.annotations.Mapper;
import org.springframework.stereotype.Component;

/**
 * 通知公告类型
 *
 */
@Mapper
public interface NoticeTypeDao extends BaseMapper<NoticeTypeEntity> {

}
