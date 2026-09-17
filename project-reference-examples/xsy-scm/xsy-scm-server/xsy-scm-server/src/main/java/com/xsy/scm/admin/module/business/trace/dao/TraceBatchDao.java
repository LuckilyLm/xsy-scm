package com.xsy.scm.admin.module.business.trace.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xsy.scm.admin.module.business.trace.domain.entity.TraceBatchEntity;
import com.xsy.scm.admin.module.business.trace.domain.form.TraceBatchQueryForm;
import com.xsy.scm.admin.module.business.trace.domain.vo.TraceBatchVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 溯源批次 Dao
 *
 * @author xsy-scm
 */
@Mapper
public interface TraceBatchDao extends BaseMapper<TraceBatchEntity> {

    /**
     * 分页查询溯源批次
     */
    List<TraceBatchVO> queryPage(Page page, @Param("queryForm") TraceBatchQueryForm queryForm);

    /**
     * 批量更新删除状态
     */
    void batchUpdateDeleted(@Param("idList") List<Long> idList,
                            @Param("deletedFlag") Boolean deletedFlag);
}
