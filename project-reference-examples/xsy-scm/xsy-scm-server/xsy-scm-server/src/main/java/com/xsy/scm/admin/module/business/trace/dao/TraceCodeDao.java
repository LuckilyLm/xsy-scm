package com.xsy.scm.admin.module.business.trace.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xsy.scm.admin.module.business.trace.domain.entity.TraceCodeEntity;
import com.xsy.scm.admin.module.business.trace.domain.form.TraceCodeQueryForm;
import com.xsy.scm.admin.module.business.trace.domain.vo.TraceCodeVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 溯源码 Dao
 *
 * @author xsy-scm
 */
@Mapper
public interface TraceCodeDao extends BaseMapper<TraceCodeEntity> {

    /**
     * 分页查询溯源码
     */
    List<TraceCodeVO> queryPage(Page page, @Param("queryForm") TraceCodeQueryForm queryForm);

    /**
     * 按溯源码查询（扫码入口）
     */
    TraceCodeEntity getByTraceCode(@Param("traceCode") String traceCode);

    /**
     * 统计批次下已生成的溯源码数量
     */
    Long countByBatchId(@Param("batchId") Long batchId);

    /**
     * 批量更新删除状态
     */
    void batchUpdateDeleted(@Param("idList") List<Long> idList,
                            @Param("deletedFlag") Boolean deletedFlag);
}
