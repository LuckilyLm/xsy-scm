package com.xsy.scm.base.module.support.job.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xsy.scm.base.module.support.job.api.domain.XsyJobLogQueryForm;
import com.xsy.scm.base.module.support.job.api.domain.XsyJobLogVO;
import com.xsy.scm.base.module.support.job.repository.domain.XsyJobLogEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 定时任务-执行记录 dao
 *
 */
@Mapper
public interface XsyJobLogDao extends BaseMapper<XsyJobLogEntity> {

    /**
     * 定时任务-执行记录-分页查询
     *
     * @param page
     * @param queryForm
     * @return
     */
    List<XsyJobLogVO> query(Page<?> page, @Param("query") XsyJobLogQueryForm queryForm);
}
