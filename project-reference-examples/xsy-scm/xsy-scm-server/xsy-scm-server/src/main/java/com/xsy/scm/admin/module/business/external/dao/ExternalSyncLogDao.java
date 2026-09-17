package com.xsy.scm.admin.module.business.external.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xsy.scm.admin.module.business.external.domain.entity.ExternalSyncLogEntity;
import com.xsy.scm.admin.module.business.external.domain.form.ExternalSyncLogQueryForm;
import com.xsy.scm.admin.module.business.external.domain.vo.ExternalSyncLogVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 外部平台同步日志 Dao
 *
 * @author xsy-scm
 */
@Mapper
public interface ExternalSyncLogDao extends BaseMapper<ExternalSyncLogEntity> {

    /**
     * 分页查询同步日志
     */
    List<ExternalSyncLogVO> queryPage(Page page, @Param("queryForm") ExternalSyncLogQueryForm queryForm);

    /**
     * 批量更新删除状态
     */
    void batchUpdateDeleted(@Param("idList") List<Long> idList,
                            @Param("deletedFlag") Boolean deletedFlag);
}
