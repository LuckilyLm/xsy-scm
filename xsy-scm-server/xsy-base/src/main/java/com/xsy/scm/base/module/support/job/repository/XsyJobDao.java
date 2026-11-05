package com.xsy.scm.base.module.support.job.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xsy.scm.base.module.support.job.api.domain.XsyJobQueryForm;
import com.xsy.scm.base.module.support.job.api.domain.XsyJobVO;
import com.xsy.scm.base.module.support.job.repository.domain.XsyJobEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 定时任务 dao
 *
 */
@Mapper
public interface XsyJobDao extends BaseMapper<XsyJobEntity> {

    /**
     * 定时任务-分页查询
     *
     * @param page
     * @param queryForm
     * @return
     */
    List<XsyJobVO> query(Page<?> page, @Param("query") XsyJobQueryForm queryForm);

    /**
     * 假删除
     *
     * @param jobId
     * @return
     */
    void updateDeletedFlag(@Param("jobId") Integer jobId, @Param("deletedFlag") Boolean deletedFlag);

    /**
     * 根据 任务class 查找
     *
     * @param jobClass
     * @return
     */
    XsyJobEntity selectByJobClass(@Param("jobClass") String jobClass);
}
