package com.xsy.scm.admin.module.business.trace.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xsy.scm.admin.module.business.trace.domain.entity.TraceInspectEntity;
import com.xsy.scm.admin.module.business.trace.domain.form.TraceInspectQueryForm;
import com.xsy.scm.admin.module.business.trace.domain.vo.TraceInspectVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 检测报告 Dao
 *
 * @author xsy-scm
 */
@Mapper
public interface TraceInspectDao extends BaseMapper<TraceInspectEntity> {

    /**
     * 分页查询检测报告
     */
    List<TraceInspectVO> queryPage(Page page, @Param("queryForm") TraceInspectQueryForm queryForm);

    /**
     * 批量更新删除状态
     */
    void batchUpdateDeleted(@Param("idList") List<Long> idList,
                            @Param("deletedFlag") Boolean deletedFlag);
}
